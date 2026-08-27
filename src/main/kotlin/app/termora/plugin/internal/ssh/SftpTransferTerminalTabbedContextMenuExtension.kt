package app.termora.plugin.internal.ssh

import app.termora.Actions
import app.termora.HostTerminalTab
import app.termora.I18n
import app.termora.TerminalTab
import app.termora.TerminalTabbedContextMenuExtension
import app.termora.WindowScope
import app.termora.actions.ActionManager
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.protocol.TransferProtocolProvider
import app.termora.transfer.TransferActionEvent
import javax.swing.JMenuItem

class SftpTransferTerminalTabbedContextMenuExtension private constructor() : TerminalTabbedContextMenuExtension {
    companion object {
        val instance = SftpTransferTerminalTabbedContextMenuExtension()
    }

    private val actionManager = ActionManager.getInstance()

    override fun createJMenuItem(
        windowScope: WindowScope,
        tab: TerminalTab
    ): JMenuItem {
        if (tab is HostTerminalTab) {
            // 仅当主机协议支持传输时才显示
            if (TransferProtocolProvider.valueOf(tab.host.protocol) != null) {
                val sftpTransfer = JMenuItem(I18n.getString("termora.tabbed.contextmenu.sftp-transfer"))
                sftpTransfer.addActionListener(object : AnAction() {
                    override fun actionPerformed(evt: AnActionEvent) {
                        // 打开 SFTP 传输 tab 并连接当前会话主机
                        actionManager.getAction(Actions.SFTP)
                            ?.actionPerformed(TransferActionEvent(evt.source, tab.host, evt))
                    }
                })
                return sftpTransfer
            }
        }
        throw UnsupportedOperationException()
    }

    override fun ordered(): Long {
        // 排在“连接到 SFTP Command”之后
        return 2
    }
}
