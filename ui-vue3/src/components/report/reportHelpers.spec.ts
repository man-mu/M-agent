import { describe, expect, it } from 'vitest'
import { deriveReportSources, deriveReportToc, reportStatusView } from './helpers'

describe('report helpers', () => {
  it('derives a markdown heading toc without reading fenced code', () => {
    const toc = deriveReportToc(`# 总结

正文

## [发现一](https://example.com)

\`\`\`md
### 不应出现
\`\`\`

### **下一步**
`)

    expect(toc).toEqual([
      { key: '总结-1', level: 1, title: '总结', anchor: '总结-1' },
      { key: '发现一-1', level: 2, title: '发现一', anchor: '发现一-1' },
      { key: '下一步-1', level: 3, title: '下一步', anchor: '下一步-1' },
    ])
  })

  it('does not invent a toc when the report has no headings', () => {
    expect(deriveReportToc('只有普通段落\n- 列表')).toEqual([])
  })

  it('deduplicates real source links from stream events and skips missing urls', () => {
    const sources = deriveReportSources([
      {
        site_information: [
          { title: 'A', url: 'https://example.com/a' },
          { title: 'No url' },
        ],
      },
      {
        payload: {
          siteInformation: [
            { title: 'A again', url: 'https://example.com/a' },
            { title: 'B', link: 'https://news.example.org/b' },
          ],
        },
      },
    ])

    expect(sources).toEqual([
      { title: 'A', url: 'https://example.com/a', host: 'example.com' },
      { title: 'B', url: 'https://news.example.org/b', host: 'news.example.org' },
    ])
  })

  it('maps report empty and error states explicitly', () => {
    expect(reportStatusView({
      hasThread: false,
      hasContent: false,
      loading: false,
      error: '',
    })).toMatchObject({ kind: 'not-started', label: '等待研究' })

    expect(reportStatusView({
      hasThread: true,
      hasContent: false,
      loading: false,
      error: '',
    })).toMatchObject({ kind: 'not-generated', label: '尚未生成' })

    expect(reportStatusView({
      hasThread: true,
      hasContent: false,
      loading: false,
      error: '报告不存在或尚未生成。',
      notGenerated: true,
    })).toMatchObject({ kind: 'not-generated', emptyDescription: '报告尚未生成，研究完成后会自动显示。' })

    expect(reportStatusView({
      hasThread: true,
      hasContent: false,
      loading: false,
      error: '网络错误',
    })).toMatchObject({ kind: 'read-failed', emptyDescription: '网络错误' })
  })
})
