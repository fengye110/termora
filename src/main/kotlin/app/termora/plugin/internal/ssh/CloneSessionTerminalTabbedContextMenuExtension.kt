package app.termora.plugin.internal.ssh

import app.termora.I18n
import app.termora.TerminalTab
import app.termora.TerminalTabbedContextMenuExtension
import app.termora.TerminalTabbedManager
import app.termora.WindowScope
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.actions.DataProviders
import javax.swing.JMenuItem

class CloneSessionTerminalTabbedContextMenuExtension private constructor() : TerminalTabbedContextMenuExtension {
    companion object {
        val instance = CloneSessionTerminalTabbedContextMenuExtension()

        /**
         * 克隆会话：复用当前 SSH 会话，在新的 tab 中打开一个 channel
         *
         * @return 返回 true 表示执行了克隆；tab 不是已连接的 SSH 会话时返回 false
         */
        fun cloneSession(
            windowScope: WindowScope,
            tab: TerminalTab,
            terminalTabbedManager: TerminalTabbedManager
        ): Boolean {
            if (tab !is SSHTerminalTab) return false
            if (tab.host.protocol != SSHProtocolProvider.PROTOCOL) return false
            val c = tab.getData(SSHTerminalTab.MySshHandler) ?: return false
            if (c.channel?.isOpen != true) return false

            val index = terminalTabbedManager.indexOfTerminalTab(tab)
            val handler = c.copy(channel = null)
            val newTab = SSHTerminalTab(windowScope, tab.host, handler)
            if (index >= 0) {
                terminalTabbedManager.addTerminalTab(index + 1, newTab)
            } else {
                terminalTabbedManager.addTerminalTab(newTab)
            }
            newTab.start()
            return true
        }
    }

    override fun createJMenuItem(
        windowScope: WindowScope,
        tab: TerminalTab
    ): JMenuItem {
        if (tab is SSHTerminalTab) {
            if (tab.host.protocol == SSHProtocolProvider.PROTOCOL) {
                val cloneSession = JMenuItem(I18n.getString("termora.tabbed.contextmenu.clone-session"))
                val c = tab.getData(SSHTerminalTab.MySshHandler)
                cloneSession.isEnabled = c?.channel?.isOpen == true
                if (c != null) {
                    cloneSession.addActionListener(object : AnAction() {
                        override fun actionPerformed(evt: AnActionEvent) {
                            val terminalTabbedManager = evt.getData(DataProviders.TerminalTabbedManager) ?: return
                            cloneSession(windowScope, tab, terminalTabbedManager)
                        }
                    })
                }
                return cloneSession
            }
        }
        throw UnsupportedOperationException()
    }

    override fun ordered(): Long {
        return 0
    }

}