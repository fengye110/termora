package app.termora

import app.termora.database.DatabaseSecret
import java.awt.BorderLayout
import java.awt.GridLayout
import java.security.MessageDigest
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPasswordField

internal class SecurityOption : JPanel(BorderLayout()), OptionsPane.Option {
    private val secret = DatabaseSecret.getInstance()
    private val status = JLabel()
    private val enable = JButton("Enable Master Password Protection")
    private val change = JButton("Change Master Password")
    private val disable = JButton("Disable Master Password Protection")

    init {
        border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
        val content = JPanel(BorderLayout(0, 12))
        content.add(status, BorderLayout.NORTH)
        content.add(JLabel("If the master password is lost, encrypted local data cannot be recovered."), BorderLayout.CENTER)
        val buttons = JPanel(GridLayout(0, 1, 0, 8))
        buttons.add(enable)
        buttons.add(change)
        buttons.add(disable)
        content.add(buttons, BorderLayout.SOUTH)
        add(content, BorderLayout.NORTH)
        enable.addActionListener { enableProtection() }
        change.addActionListener { changePassword() }
        disable.addActionListener { disableProtection() }
        refresh()
    }

    override fun getIcon(isSelected: Boolean) = if (secret.isProtectionEnabled()) Icons.locked else Icons.unlocked
    override fun getTitle(): String = "Security / Master Password"
    override fun getJComponent(): JComponent = this

    private fun enableProtection() {
        val password = requestPassword("Set master password") ?: return
        val confirmation = requestPassword("Confirm master password")
        try {
            if (password.size < 1) {
                showError("Master password must contain at least 1 characters.")
            } else if (confirmation == null || !constantTimeEquals(password, confirmation)) {
                showError("Master passwords do not match.")
            } else {
                secret.enable(password)
                refresh()
            }
        } catch (e: Exception) {
            showError("Unable to enable master password protection.")
        } finally {
            password.fill('\u0000')
            confirmation?.fill('\u0000')
        }
    }

    private fun changePassword() {
        val old = requestPassword("Current master password") ?: return
        val password = requestPassword("New master password")
        val confirmation = requestPassword("Confirm new master password")
        try {
            if (password == null || password.size < 1 || confirmation == null || !constantTimeEquals(password, confirmation)) {
                showError("New master passwords must match and contain at least 1 characters.")
            } else {
                secret.change(old, password)
                refresh()
            }
        } catch (_: Exception) {
            showError("Current master password is incorrect or key data is corrupted.")
        } finally {
            old.fill('\u0000')
            password?.fill('\u0000')
            confirmation?.fill('\u0000')
        }
    }

    private fun disableProtection() {
        if (JOptionPane.showConfirmDialog(this, "The database key will be stored unencrypted. Continue?", "Disable protection", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return
        val password = requestPassword("Current master password") ?: return
        try {
            secret.disable(password)
            refresh()
        } catch (_: Exception) {
            showError("Current master password is incorrect or key data is corrupted.")
        } finally {
            password.fill('\u0000')
        }
    }

    private fun requestPassword(title: String): CharArray? {
        val field = JPasswordField()
        return if (JOptionPane.showConfirmDialog(this, field, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) field.password else null
    }

    private fun refresh() {
        val protected = secret.isProtectionEnabled()
        status.text = if (protected) "Master Password protection is enabled." else "Master Password protection is not enabled."
        enable.isEnabled = !protected
        change.isEnabled = protected
        disable.isEnabled = protected
    }

    private fun showError(message: String) {
        JOptionPane.showMessageDialog(this, message, "Security", JOptionPane.ERROR_MESSAGE)
    }

    private fun constantTimeEquals(first: CharArray, second: CharArray): Boolean {
        var difference = first.size xor second.size
        for (index in 0 until maxOf(first.size, second.size)) {
            val a = if (index < first.size) first[index].code else 0
            val b = if (index < second.size) second[index].code else 0
            difference = difference or (a xor b)
        }
        return difference == 0
    }
}
