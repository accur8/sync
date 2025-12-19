package a8.hermes.auth

import a8.hermes.core.Mailbox
import a8.hermes.rpc.RpcClient
import a8.hermes.proto.auth.auth.{LoginBeginRequest, LoginBeginResponse, LoginCompleteRequest, LoginCompleteResponse}
import a8.shared.app.Ctx
import a8.common.logging.Logging
import a8.shared.FileSystem

import java.nio.file.{Files, Paths, StandardOpenOption}
import java.util.Base64
import scala.sys.process.*
import scala.util.{Try, Success, Failure}

/**
 * SSH-based authentication for Hermes.
 *
 * Authentication Flow (from godev specs):
 * 1. LoginBegin: Send SSH public key → receive session_id + nonce
 * 2. Sign nonce: Use ssh-keygen subprocess to sign nonce
 * 3. LoginComplete: Send session_id + signature → receive auth_token
 *
 * Token Format:
 * - Opaque token string returned by auth service
 * - Stored in qubes.auth database table
 */
object SshAuth extends Logging {

  case class Config(
    sshPrivateKeyPath: String = "~/.ssh/id_ed25519",
    sshPublicKeyPath: String = "~/.ssh/id_ed25519.pub",
    authServiceMailbox: Mailbox.MailboxAddress,
  )

  /**
   * Result of authentication
   */
  case class AuthResult(
    authToken: String,
    expiresAt: java.time.Instant,
  )

  /**
   * Authenticate using SSH key
   */
  def authenticate(
    config: Config,
    rpcClient: RpcClient,
  )(using ctx: Ctx): Try[AuthResult] = {
    Try {
      logger.info("Starting SSH authentication...")

      // Step 1: Read SSH public key
      val publicKey = readPublicKey(config.sshPublicKeyPath)
      logger.debug(s"Loaded SSH public key from ${config.sshPublicKeyPath}")

      // Step 2: LoginBegin - get session_id and nonce
      logger.info("Calling LoginBegin...")
      val loginBeginRequest = LoginBeginRequest(
        sshPublicKey = publicKey,
        origin = s"${System.getProperty("user.name")}@${java.net.InetAddress.getLocalHost.getHostName}",
        vpnIp = "", // Optional VPN IP
      )

      val loginBeginResponse = rpcClient.callTyped[LoginBeginRequest, LoginBeginResponse](
        targetMailbox = config.authServiceMailbox,
        endpoint = "auth.v2.LoginBegin",
        request = loginBeginRequest,
        timeout = Some(scala.concurrent.duration.FiniteDuration(10, "seconds")),
      )(using ctx, summon).getOrElse {
        throw new RuntimeException("LoginBegin failed: no response from auth service")
      }

      val sessionId = loginBeginResponse.sessionId
      val nonce = loginBeginResponse.nonce.toByteArray
      logger.info(s"✓ Received session_id and nonce (${nonce.length} bytes)")

      // Step 3: Sign nonce with ssh-keygen
      logger.info("Signing nonce with ssh-keygen...")
      val signature = signNonce(nonce, config.sshPrivateKeyPath)
      logger.info(s"✓ Nonce signed (${signature.length} bytes)")

      // Step 4: LoginComplete - send signature, receive auth token
      logger.info("Calling LoginComplete...")
      val loginCompleteRequest = LoginCompleteRequest(
        sessionId = sessionId,
        sshPublicKey = publicKey,
        signature = com.google.protobuf.ByteString.copyFrom(signature),
      )

      val loginCompleteResponse = rpcClient.callTyped[LoginCompleteRequest, LoginCompleteResponse](
        targetMailbox = config.authServiceMailbox,
        endpoint = "auth.v2.LoginComplete",
        request = loginCompleteRequest,
        timeout = Some(scala.concurrent.duration.FiniteDuration(10, "seconds")),
      )(using ctx, summon).getOrElse {
        throw new RuntimeException("LoginComplete failed: no response from auth service")
      }

      val authToken = loginCompleteResponse.authToken
      logger.info(s"✓ Authentication successful!")

      // Step 3: Token expiration
      // TODO: Extract expiration from GetUserInfoForSelf once proto is fixed
      // For now, assume 24 hour expiration
      val expiresAt = java.time.Instant.now().plusSeconds(86400)
      logger.info(s"✓ Token will be renewed in 12 hours (assuming 24h expiration, expires at: $expiresAt)")

      AuthResult(authToken, expiresAt)
    }
  }

  /**
   * Read SSH public key from file
   */
  private def readPublicKey(publicKeyPath: String): String = {
    val expanded = expandHome(publicKeyPath)
    val path = Paths.get(expanded)

    if (!Files.exists(path)) {
      throw new RuntimeException(s"SSH public key not found: $expanded")
    }

    val content = new String(Files.readAllBytes(path), "UTF-8").trim

    // Validate it's an SSH public key
    if (!content.startsWith("ssh-")) {
      throw new RuntimeException(s"Invalid SSH public key format in $expanded")
    }

    content
  }

  /**
   * Sign nonce using ssh-keygen subprocess
   *
   * Command: echo -n <nonce_base64> | ssh-keygen -Y sign -f ~/.ssh/id_ed25519 -n nefario
   */
  private def signNonce(nonce: Array[Byte], privateKeyPath: String): Array[Byte] = {
    val expanded = expandHome(privateKeyPath)
    val noncePath = Paths.get(expanded)

    if (!Files.exists(noncePath)) {
      throw new RuntimeException(s"SSH private key not found: $expanded")
    }

    // Write nonce to temporary file (ssh-keygen reads from file)
    val tempNonceFile = Files.createTempFile("hermes-nonce-", ".bin")
    try {
      Files.write(tempNonceFile, nonce)

      // Run ssh-keygen to sign the nonce
      // Command: ssh-keygen -Y sign -f <private_key> -n auth-challenge < <nonce_file>
      val command = Seq(
        "ssh-keygen",
        "-Y", "sign",
        "-f", expanded,
        "-n", "auth-challenge",
      )

      logger.debug(s"Executing: ${command.mkString(" ")} < ${tempNonceFile}")

      val processBuilder = Process(command)
      val stdin = Files.newInputStream(tempNonceFile)

      var stdout = Array.empty[Byte]
      var stderr = ""

      val io = new ProcessIO(
        in => {
          // Provide nonce as stdin
          try {
            val buffer = new Array[Byte](8192)
            var bytesRead = 0
            while ({ bytesRead = stdin.read(buffer); bytesRead != -1 }) {
              in.write(buffer, 0, bytesRead)
            }
          } finally {
            in.close()
            stdin.close()
          }
        },
        out => {
          // Capture stdout (signature)
          try {
            val baos = new java.io.ByteArrayOutputStream()
            val buffer = new Array[Byte](8192)
            var bytesRead = 0
            while ({ bytesRead = out.read(buffer); bytesRead != -1 }) {
              baos.write(buffer, 0, bytesRead)
            }
            stdout = baos.toByteArray
          } finally {
            out.close()
          }
        },
        err => {
          // Capture stderr (errors)
          try {
            val reader = scala.io.Source.fromInputStream(err)
            stderr = reader.mkString
            reader.close()
          } finally {
            err.close()
          }
        }
      )

      val exitCode = processBuilder.run(io).exitValue()

      if (exitCode != 0) {
        throw new RuntimeException(s"ssh-keygen failed with exit code $exitCode: $stderr")
      }

      if (stdout.isEmpty) {
        throw new RuntimeException("ssh-keygen produced no signature output")
      }

      // Parse PEM format signature and extract binary wire format
      // ssh-keygen produces:
      // -----BEGIN SSH SIGNATURE-----
      // <base64 encoded binary signature>
      // -----END SSH SIGNATURE-----
      val pemString = new String(stdout, "UTF-8")
      val lines = pemString.split("\n").map(_.trim).filter(_.nonEmpty)

      // Remove PEM header and footer
      val base64Content = lines
        .filterNot(_.startsWith("-----BEGIN"))
        .filterNot(_.startsWith("-----END"))
        .mkString("")

      if (base64Content.isEmpty) {
        throw new RuntimeException(s"Failed to extract base64 content from SSH signature: $pemString")
      }

      // Decode base64 to get SSH signature blob
      val sigBlob = Base64.getDecoder.decode(base64Content)

      // Parse SSH signature blob to extract just the signature field
      // Blob structure:
      //   magic: "SSHSIG" (6 bytes)
      //   version: uint32
      //   public_key: ssh-wire-string
      //   namespace: ssh-wire-string
      //   reserved: ssh-wire-string
      //   hash_algo: ssh-wire-string
      //   signature: ssh-wire-string <- THIS is what we want
      logger.debug(s"SSH signature blob length: ${sigBlob.length} bytes")
      val sshWireSignature = parseSSHSignatureBlob(sigBlob)
      logger.debug(s"Extracted SSH wire signature length: ${sshWireSignature.length} bytes")

      // The signature field is in SSH wire format:
      //   string(algorithm): uint32 length + algorithm name bytes
      //   string(signature): uint32 length + raw signature bytes
      // The godev server expects this full wire format (algorithm + signature)
      sshWireSignature

    } finally {
      // Clean up temp file
      Files.deleteIfExists(tempNonceFile)
    }
  }

  /**
   * Parse SSH signature blob to extract the signature field
   *
   * SSH signature blob format (from ssh-keygen -Y sign):
   *   magic: "SSHSIG" (6 bytes)
   *   version: uint32
   *   public_key: ssh-wire-string
   *   namespace: ssh-wire-string
   *   reserved: ssh-wire-string
   *   hash_algo: ssh-wire-string
   *   signature: ssh-wire-string <- THIS is what we extract
   */
  private def parseSSHSignatureBlob(sigBlob: Array[Byte]): Array[Byte] = {
    // Check magic bytes
    if (sigBlob.length < 6) {
      throw new RuntimeException("Signature blob too short")
    }
    val magic = new String(sigBlob.slice(0, 6), "UTF-8")
    if (magic != "SSHSIG") {
      throw new RuntimeException(s"Invalid signature magic bytes: $magic")
    }

    var offset = 6

    // Read version (uint32)
    if (sigBlob.length < offset + 4) {
      throw new RuntimeException("Not enough data for version")
    }
    val version = (sigBlob(offset).toInt & 0xFF) << 24 |
                  (sigBlob(offset + 1).toInt & 0xFF) << 16 |
                  (sigBlob(offset + 2).toInt & 0xFF) << 8 |
                  (sigBlob(offset + 3).toInt & 0xFF)
    offset += 4

    if (version != 1) {
      throw new RuntimeException(s"Unsupported signature version: $version")
    }

    // Skip 4 fields: public_key, namespace, reserved, hash_algo
    for (i <- 0 until 4) {
      if (sigBlob.length < offset + 4) {
        throw new RuntimeException(s"Not enough data for field $i length")
      }
      val length = (sigBlob(offset).toInt & 0xFF) << 24 |
                   (sigBlob(offset + 1).toInt & 0xFF) << 16 |
                   (sigBlob(offset + 2).toInt & 0xFF) << 8 |
                   (sigBlob(offset + 3).toInt & 0xFF)
      offset += 4

      if (sigBlob.length < offset + length) {
        throw new RuntimeException(s"Not enough data for field $i data (length=$length)")
      }
      offset += length
    }

    // Read the signature field (field 5)
    if (sigBlob.length < offset + 4) {
      throw new RuntimeException("Not enough data for signature length")
    }
    val sigLength = (sigBlob(offset).toInt & 0xFF) << 24 |
                    (sigBlob(offset + 1).toInt & 0xFF) << 16 |
                    (sigBlob(offset + 2).toInt & 0xFF) << 8 |
                    (sigBlob(offset + 3).toInt & 0xFF)
    offset += 4

    if (sigBlob.length < offset + sigLength) {
      throw new RuntimeException(s"Not enough data for signature (length=$sigLength)")
    }

    sigBlob.slice(offset, offset + sigLength)
  }

  /**
   * Parse SSH wire format signature to extract raw signature bytes
   *
   * SSH wire signature format:
   *   string(algorithm): uint32 length + algorithm name bytes (e.g., "ssh-ed25519")
   *   string(signature): uint32 length + raw signature bytes (64 bytes for Ed25519)
   */
  private def parseSSHWireSignature(sshWireSignature: Array[Byte]): Array[Byte] = {
    var offset = 0

    // Read algorithm name (and skip it)
    if (sshWireSignature.length < offset + 4) {
      throw new RuntimeException("Not enough data for algorithm length")
    }
    val algoLength = (sshWireSignature(offset).toInt & 0xFF) << 24 |
                     (sshWireSignature(offset + 1).toInt & 0xFF) << 16 |
                     (sshWireSignature(offset + 2).toInt & 0xFF) << 8 |
                     (sshWireSignature(offset + 3).toInt & 0xFF)
    offset += 4

    if (sshWireSignature.length < offset + algoLength) {
      throw new RuntimeException(s"Not enough data for algorithm name (length=$algoLength)")
    }
    val algoName = new String(sshWireSignature.slice(offset, offset + algoLength), "UTF-8")
    logger.debug(s"SSH signature algorithm: $algoName")
    offset += algoLength

    // Read raw signature bytes
    if (sshWireSignature.length < offset + 4) {
      throw new RuntimeException("Not enough data for signature length")
    }
    val sigLength = (sshWireSignature(offset).toInt & 0xFF) << 24 |
                    (sshWireSignature(offset + 1).toInt & 0xFF) << 16 |
                    (sshWireSignature(offset + 2).toInt & 0xFF) << 8 |
                    (sshWireSignature(offset + 3).toInt & 0xFF)
    offset += 4

    if (sshWireSignature.length < offset + sigLength) {
      throw new RuntimeException(s"Not enough data for signature bytes (length=$sigLength)")
    }

    sshWireSignature.slice(offset, offset + sigLength)
  }

  /**
   * Expand ~ in file paths
   */
  private def expandHome(path: String): String = {
    if (path.startsWith("~/")) {
      System.getProperty("user.home") + path.substring(1)
    } else {
      path
    }
  }

}
