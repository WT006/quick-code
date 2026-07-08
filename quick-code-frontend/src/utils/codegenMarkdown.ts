/**
 * 代码生成对话的 Markdown 预处理：
 * 1. 步骤文字与 ``` 围栏分行，避免围栏无法识别
 * 2. 流式未闭合围栏临时补全，防止 HTML 被当作页面渲染
 */
export function prepareCodegenMarkdown(content: string, streaming = false): string {
  if (!content) {
    return ''
  }

  let normalized = content
    // 步骤文案已在状态栏展示，正文中移除（含粘连重复如「代码生成中代码生成中」）
    .replace(
      /HTML代码生成中|CSS代码生成中|JavaScript代码生成中|代码生成中|代码文件已生成/g,
      '',
    )
    // 步骤文案与代码围栏粘连时拆开
    .replace(
      /(HTML代码生成中|CSS代码生成中|JavaScript代码生成中|代码生成中|代码文件已生成)\s*(```)/gi,
      '$1\n\n$2',
    )
    // 围栏语言标识后补换行，便于 markdown-it 识别
    .replace(/```(html|css|javascript|js)\s*(?=\S)/gi, '```$1\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim()

  if (streaming) {
    const fenceCount = (normalized.match(/```/g) || []).length
    if (fenceCount % 2 === 1) {
      normalized += '\n```'
    }
  }

  return normalized
}
