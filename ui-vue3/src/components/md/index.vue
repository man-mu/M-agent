<template>
  <div class="markdown-body" v-html="renderedContent"></div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import katex from 'markdown-it-katex'
import hljs from 'highlight.js'
import 'highlight.js/styles/atom-one-light.css'
import 'katex/dist/katex.min.css'

const props = defineProps<{
  content: string
}>()

const md = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
  breaks: true,
  highlight(code: string, lang: string) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return `<pre class="hljs"><code>${hljs.highlight(code, {
          language: lang,
          ignoreIllegals: true,
        }).value}</code></pre>`
      } catch {
        // Fall back to escaped plain text.
      }
    }
    return `<pre class="hljs"><code>${md.utils.escapeHtml(code)}</code></pre>`
  },
}).use(katex)

const defaultLinkOpen =
  md.renderer.rules.link_open ||
  ((tokens: any[], idx: number, options: any, env: unknown, self: any) =>
    self.renderToken(tokens, idx, options))

md.renderer.rules.link_open = (
  tokens: any[],
  idx: number,
  options: any,
  env: unknown,
  self: any,
) => {
  tokens[idx].attrSet('target', '_blank')
  tokens[idx].attrSet('rel', 'noopener noreferrer')
  return defaultLinkOpen(tokens, idx, options, env, self)
}

const renderedContent = computed(() => md.render(props.content || ''))
</script>

<style lang="less" scoped>
.markdown-body {
  color: #20242a;
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif;
  line-height: 1.75;
  overflow-wrap: anywhere;
  text-align: left;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  line-height: 1.25;
  margin: 1.25em 0 0.6em;
}

.markdown-body :deep(p) {
  margin: 0 0 0.75em;
}

.markdown-body :deep(code) {
  background-color: rgba(27, 31, 35, 0.06);
  border-radius: 4px;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  padding: 0.15em 0.35em;
}

.markdown-body :deep(pre) {
  background-color: #f6f8fa;
  border-radius: 8px;
  overflow: auto;
  padding: 14px;
}

.markdown-body :deep(pre code) {
  background: transparent;
  padding: 0;
}

.markdown-body :deep(a) {
  color: #2356f6;
}

.markdown-body :deep(blockquote) {
  border-left: 3px solid #d9e2ff;
  color: #5b6472;
  margin: 1em 0;
  padding-left: 1em;
}
</style>
