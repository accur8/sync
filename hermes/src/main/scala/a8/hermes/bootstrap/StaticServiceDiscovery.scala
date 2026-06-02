package a8.hermes.bootstrap

import a8.hermes.core.Mailbox.MailboxAddress

/**
 * Simple static service discovery using named mailboxes from config.
 * This is the PRIMARY method for service resolution in godev.
 */
class StaticServiceDiscovery(namedMailboxes: Map[String, String]) {

  /**
   * Get mailbox address for a named service.
   *
   * @param serviceName Service name (e.g., "auth", "continuum", "mailbox")
   * @throws RuntimeException if service name not found in config
   * @return Mailbox address for the service
   */
  def getMailbox(serviceName: String): MailboxAddress = {
    namedMailboxes.get(serviceName) match {
      case Some(address) => MailboxAddress(address)
      case None =>
        val available = namedMailboxes.keys.mkString(", ")
        throw new RuntimeException(
          s"Named mailbox '$serviceName' not found in bootstrap config " +
          s"(available: $available)"
        )
    }
  }

  /**
   * Get all named mailbox mappings (for debugging)
   */
  def getAllMailboxes: Map[String, String] = namedMailboxes

}
