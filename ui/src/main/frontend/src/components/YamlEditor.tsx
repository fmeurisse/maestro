import Editor from '@monaco-editor/react'
import { useEffect, useState } from 'react'

interface YamlEditorProps {
  value: string
  onChange: (value: string) => void
  readOnly?: boolean
  height?: string
}

/**
 * YAML Editor component using Monaco Editor
 * Provides syntax highlighting, validation, and editing capabilities for YAML content
 */
export function YamlEditor({
  value,
  onChange,
  readOnly = false,
  height = '600px',
}: YamlEditorProps) {
  const [isValid, setIsValid] = useState(true)

  // simple validation helper reused on change and on mount/value updates
  const validate = (val: string) => {
    try {
      const openBraces = (val.match(/{/g) || []).length
      const closeBraces = (val.match(/}/g) || []).length
      const openBrackets = (val.match(/\[/g) || []).length
      const closeBrackets = (val.match(/\]/g) || []).length

      return openBraces === closeBraces && openBrackets === closeBrackets
    } catch {
      return false
    }
  }

  const handleEditorChange = (value: string | undefined) => {
    if (value !== undefined) {
      onChange(value)
      setIsValid(validate(value))
    }
  }

  // Validate when the initial value is provided or whenever it changes
  useEffect(() => {
    setIsValid(validate(value))
  }, [value])

  return (
    <div className="yaml-editor">
      <Editor
        height={height}
        defaultLanguage="yaml"
        value={value}
        onChange={handleEditorChange}
        theme="vs-dark"
        options={{
          readOnly,
          minimap: { enabled: false },
          scrollBeyondLastLine: false,
          wordWrap: 'on',
          fontSize: 14,
          lineNumbers: 'on',
          folding: true,
          automaticLayout: true,
        }}
      />
      {!isValid && !readOnly && (
        <div
          style={{
            color: '#ff6b6b',
            marginTop: '8px',
            fontSize: '14px',
          }}
        >
          ⚠ Warning: YAML syntax may be invalid
        </div>
      )}
      <style>{`
        .yaml-editor {
          border: 1px solid #444;
          border-radius: 4px;
          overflow: hidden;
        }
      `}</style>
    </div>
  )
}
