#!/bin/bash
INPUT=$(cat)
TOOL_NAME=$(echo "$INPUT" | jq -r '.tool_name')

if [[ "$TOOL_NAME" == "Edit" ]] || [[ "$TOOL_NAME" == "Write" ]] || [[ "$TOOL_NAME" == "write_file" ]]; then
    FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // .tool_input.path // empty')
    CWD=$(echo "$INPUT" | jq -r '.cwd // empty')
    [[ "$FILE_PATH" != /* && -n "$CWD" ]] && FILE_PATH="$CWD/$FILE_PATH"

    if [[ "$FILE_PATH" == *.kt || "$FILE_PATH" == *.kts ]] && [[ -f "$FILE_PATH" ]]; then
        if ! command -v ktlint >/dev/null 2>&1; then
            echo "[Hook] ktlint not installed (brew install ktlint)" >&2
            exit 0
        fi
        echo "[Hook] Running ktlint -F for $(basename "$FILE_PATH")" >&2
        if ktlint -F --relative "$FILE_PATH" >&2 2>&1; then
            echo "[Hook] Format OK" >&2
        else
            echo "[Hook] ktlint found violations that need manual fixes" >&2
        fi
    fi
fi

exit 0
