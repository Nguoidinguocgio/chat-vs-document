<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useQueryClient } from '@tanstack/vue-query'
import { useCurrentUser } from '@/features/auth'
import { ChatPage } from '@/features/chat'
import { ConversationSidebar } from '@/features/conversations'
import { DOCUMENTS_KEY, GlobalDropOverlay, applyDocumentEvent } from '@/features/documents'
import { connectWorkspaceEvents } from '@/shared/realtime'
import ConnectionBanner from './ConnectionBanner.vue'
import ErrorBoundary from './ErrorBoundary.vue'
import RightPanel from './RightPanel.vue'
import TopBar from './TopBar.vue'
import { useLayoutStore } from './layoutStore'
import { onSessionRevoked } from './session'

// Bố cục SCR-03: thanh trên 56 px; ≥ 1280 px ba cột 280 | chính | 440 (bảng phải đóng được);
// < 1280 px bảng phải thành ngăn kéo; < 1024 px thanh trái cũng thành ngăn kéo (nút ☰).
const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const qc = useQueryClient()
const { data: user } = useCurrentUser()
const layout = useLayoutStore()
const conversationId = computed(() => (typeof route.params.id === 'string' ? route.params.id : null))

function openConversation(id: string | null) {
  layout.navOpen = false
  void router.push(id ? `/c/${id}` : '/c')
}

let disconnect: () => void = () => {}
onMounted(() => {
  // Lần đầu ở màn hình rộng: mở sẵn bảng Tài liệu (T11 mục 3.2).
  if (!layout.panelInitialized) {
    layout.panelInitialized = true
    if (window.innerWidth >= 1280) layout.openPanel('documents')
  }
  disconnect = connectWorkspaceEvents(
    (e) => {
      if (e.type === 'session.revoked') void onSessionRevoked(router)
      else applyDocumentEvent(qc, e.type, e.data)
    },
    {
      pollFallback: import.meta.env.VITE_POLL_FALLBACK === 'true',
      onPoll: () => void qc.invalidateQueries({ queryKey: DOCUMENTS_KEY }),
    },
  )
})
onBeforeUnmount(() => disconnect())
</script>

<template>
  <div v-if="user" class="shell" :class="{ 'panel-closed': layout.panel === 'closed' }">
    <a href="#composer" class="skip-link">{{ t('chat.skip_to_composer') }}</a>
    <TopBar :user="user" class="shell-top" />
    <ConnectionBanner class="shell-banner" />
    <nav id="app-nav" class="shell-nav" :class="{ open: layout.navOpen }" :aria-label="t('chat.conversations')">
      <ErrorBoundary region="nav">
        <ConversationSidebar :active-id="conversationId" @new="openConversation(null)" @open="openConversation" />
      </ErrorBoundary>
    </nav>
    <main id="app-main" class="shell-main">
      <ErrorBoundary region="chat">
        <ChatPage :conversation-id="conversationId" @open-documents="layout.openPanel('documents')" @open-citation="layout.openCitation($event)" />
      </ErrorBoundary>
    </main>
    <aside id="app-sources" class="shell-sources" :class="{ open: layout.panel !== 'closed' }" :aria-label="t('documents.panel_title')">
      <ErrorBoundary region="panel">
        <RightPanel />
      </ErrorBoundary>
    </aside>
    <div v-if="layout.navOpen || layout.panel !== 'closed'" class="shell-scrim" aria-hidden="true" @click="layout.navOpen = false; layout.closePanel()" />
    <GlobalDropOverlay @dropped="layout.openPanel('documents')" />
  </div>
</template>

<style scoped>
.shell {
  display: grid;
  height: 100vh;
  overflow: hidden;
  grid-template-rows: 56px auto minmax(0, 1fr);
  grid-template-columns: minmax(0, 1fr);
  grid-template-areas: 'top' 'banner' 'main';
}
.shell-top {
  grid-area: top;
}
.shell-banner {
  grid-area: banner;
}
.shell-main {
  grid-area: main;
  min-width: 0;
  overflow: hidden;
}
.shell-nav,
.shell-sources {
  position: fixed;
  top: 56px;
  bottom: 0;
  z-index: 40;
  background: var(--color-bg);
  box-shadow: var(--shadow-e3);
  transition: transform var(--motion-base) var(--ease);
}
.shell-nav {
  left: 0;
  width: min(280px, 85vw);
  transform: translateX(-100%);
}
.shell-sources {
  right: 0;
  width: min(440px, 92vw);
  transform: translateX(100%);
  overflow-y: auto;
}
.shell-nav.open,
.shell-sources.open {
  transform: none;
}
.shell-scrim {
  position: fixed;
  inset: 56px 0 0 0;
  z-index: 30;
  background: var(--color-text);
  opacity: 0.3;
}
@media (min-width: 1024px) {
  .shell {
    grid-template-columns: 280px minmax(0, 1fr);
    grid-template-areas: 'top top' 'banner banner' 'nav main';
  }
  .shell-nav {
    grid-area: nav;
    position: static;
    width: auto;
    transform: none;
    box-shadow: none;
    border-right: 1px solid var(--color-border);
    background: var(--color-bg-subtle);
  }
}
@media (min-width: 1280px) {
  .shell {
    grid-template-columns: 280px minmax(0, 1fr) 440px;
    grid-template-areas: 'top top top' 'banner banner banner' 'nav main sources';
  }
  .shell.panel-closed {
    grid-template-columns: 280px minmax(0, 1fr) 0;
  }
  .shell-sources {
    grid-area: sources;
    position: static;
    width: auto;
    transform: none;
    box-shadow: none;
    border-left: 1px solid var(--color-border);
  }
  .shell.panel-closed .shell-sources {
    border-left: 0;
    overflow: hidden;
  }
  .shell-scrim {
    display: none;
  }
}
.skip-link {
  position: absolute;
  left: 8px;
  top: -48px;
  z-index: 60;
  padding: 8px 12px;
  border-radius: var(--radius-md);
  background: var(--color-primary);
  color: var(--color-bg);
}
.skip-link:focus {
  top: 8px;
}
</style>
