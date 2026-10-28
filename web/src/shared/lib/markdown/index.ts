// Render Markdown an toàn bằng markdown-it + DOMPurify (T16) — nơi duy nhất được dùng v-html/innerHTML (luật R-4).
export { default as AnswerMarkdown } from './AnswerMarkdown.vue'
export { decorateCitations, placeCursor, type ChipInfo } from './citationPlugin'
export { ALLOWED_TAGS, renderSafe, splitBlocks } from './sanitizeConfig'
