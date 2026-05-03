import re
import os

files_to_process = [
    r"C:\Users\badie\Desktop\AServer-Desktop\AServer-Desktop\composeApp\src\jvmMain\kotlin\com\badiadam\aserver\ui\screens\FileManagerScreen.kt",
    r"C:\Users\badie\Desktop\AServer-Desktop\AServer-Desktop\composeApp\src\jvmMain\kotlin\com\badiadam\aserver\ui\screens\MyServersScreen.kt"
]

imports_to_add = """import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.material.Surface
import androidx.compose.foundation.ExperimentalFoundationApi
"""

tooltip_template = """TooltipArea(
{indent}    tooltip = {{
{indent}        Surface(
{indent}            modifier = Modifier.shadow(4.dp),
{indent}            color = Color(0xFF2D2D2D),
{indent}            shape = RoundedCornerShape(8.dp)
{indent}        ) {{
{indent}            Text(
{indent}                text = "{content_desc}", 
{indent}                color = Color.White, 
{indent}                fontSize = 12.sp, 
{indent}                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
{indent}            )
{indent}        }}
{indent}    }},
{indent}    delayMillis = 400,
{indent}    tooltipPlacement = TooltipPlacement.CursorPoint(
{indent}        alignment = Alignment.BottomEnd,
{indent}        offset = DpOffset(0.dp, 16.dp)
{indent}    )
{indent}) {{
{indent}    {original_button}
{indent}}}"""

def process_file(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Add imports
    if "import androidx.compose.foundation.TooltipArea" not in content:
        # Find last import
        import_idx = content.rfind("import ")
        if import_idx != -1:
            end_of_line = content.find("\n", import_idx)
            content = content[:end_of_line+1] + imports_to_add + content[end_of_line+1:]
    
    # Add OptIn to MyServersScreen
    if "fun MyServersScreen" in content and "@OptIn(ExperimentalFoundationApi::class)" not in content:
        content = content.replace("@Composable\nfun MyServersScreen", "@OptIn(ExperimentalFoundationApi::class)\n@Composable\nfun MyServersScreen")
    
    # Add OptIn to FileManagerScreen
    if "fun FileManagerScreen" in content:
        if "@OptIn(ExperimentalMaterialApi::class)" in content:
            content = content.replace("@OptIn(ExperimentalMaterialApi::class)", "@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)")
    
    # Find all IconButton blocks
    # A simple regex won't work well due to nested brackets. We can do bracket matching.
    
    def find_icon_buttons(text):
        idx = 0
        buttons = []
        while True:
            # We must capture the indentation
            match = re.search(r'([ \t]*)IconButton\(', text[idx:])
            if not match:
                break
            
            start_idx = idx + match.start()
            indent = match.group(1)
            
            # Find the matching closing bracket for IconButton(...) { ... }
            # Usually it's IconButton(...) { ... }
            # Let's find the closing brace '}'
            # We will count braces.
            brace_count = 0
            paren_count = 0
            i = start_idx
            found_open_brace = False
            while i < len(text):
                if text[i] == '(':
                    paren_count += 1
                elif text[i] == ')':
                    paren_count -= 1
                elif text[i] == '{':
                    brace_count += 1
                    found_open_brace = True
                elif text[i] == '}':
                    brace_count -= 1
                    if found_open_brace and brace_count == 0:
                        break
                i += 1
            
            end_idx = i + 1
            
            original_button = text[start_idx:end_idx]
            
            # Find contentDescription
            # Icon(..., contentDescription = "...", ...)
            cd_match = re.search(r'contentDescription\s*=\s*"([^"]+)"', original_button)
            content_desc = cd_match.group(1) if cd_match else "İşlem"
            
            buttons.append((start_idx, end_idx, indent, original_button, content_desc))
            
            idx = end_idx
        return buttons

    buttons = find_icon_buttons(content)
    # Replace from back to front to preserve indices
    for start_idx, end_idx, indent, original_button, content_desc in reversed(buttons):
        # We need to indent original_button by 4 spaces (or 1 tab) for each line except the first which we will just place inside
        # Wait, the first line already has `indent`.
        # Let's strip the `indent` from `original_button` to re-indent it properly.
        # Actually, simpler: replace newlines with newline + 4 spaces
        indented_button = original_button.replace("\n", "\n    ")
        
        # Remove the leading indent from original_button for the first line, as we will use template indentation
        indented_button = indented_button[len(indent):]
        
        replacement = tooltip_template.format(
            indent=indent,
            content_desc=content_desc,
            original_button=indented_button
        )
        content = content[:start_idx] + replacement + content[end_idx:]

    with open(file_path, "w", encoding="utf-8") as f:
        f.write(content)

for fp in files_to_process:
    process_file(fp)
    print(f"Processed {fp}")
