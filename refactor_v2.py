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

tooltip_template = """{indent}TooltipArea(
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

def find_icon_buttons(text):
    idx = 0
    buttons = []
    while True:
        match = re.search(r'([ \t]*)IconButton\s*\(', text[idx:])
        if not match:
            break
        
        start_idx = idx + match.start()
        indent = match.group(1)
        
        paren_start = start_idx + text[start_idx:].find('(')
        paren_count = 1
        i = paren_start + 1
        while i < len(text) and paren_count > 0:
            if text[i] == '(':
                paren_count += 1
            elif text[i] == ')':
                paren_count -= 1
            i += 1
        
        paren_end = i
        
        brace_start = text.find('{', paren_end)
        if brace_start == -1 or brace_start > paren_end + 20: 
            idx = paren_end
            continue
            
        brace_count = 1
        i = brace_start + 1
        while i < len(text) and brace_count > 0:
            if text[i] == '{':
                brace_count += 1
            elif text[i] == '}':
                brace_count -= 1
            i += 1
            
        end_idx = i
        original_button = text[start_idx:end_idx]
        
        cd_match = re.search(r'contentDescription\s*=\s*"([^"]+)"', original_button)
        content_desc = cd_match.group(1) if cd_match else "İşlem"
        
        buttons.append((start_idx, end_idx, indent, original_button, content_desc))
        
        idx = end_idx
    return buttons

def process_file(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    if "import androidx.compose.foundation.TooltipArea" not in content:
        import_idx = content.rfind("import ")
        if import_idx != -1:
            end_of_line = content.find("\n", import_idx)
            content = content[:end_of_line+1] + imports_to_add + content[end_of_line+1:]
    
    if "fun MyServersScreen" in content and "@OptIn(ExperimentalFoundationApi::class)" not in content:
        content = content.replace("@Composable\nfun MyServersScreen", "@OptIn(ExperimentalFoundationApi::class)\n@Composable\nfun MyServersScreen")
    
    if "fun FileManagerScreen" in content:
        if "@OptIn(ExperimentalMaterialApi::class)" in content:
            content = content.replace("@OptIn(ExperimentalMaterialApi::class)", "@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)")
    
    buttons = find_icon_buttons(content)
    for start_idx, end_idx, indent, original_button, content_desc in reversed(buttons):
        indented_button = original_button.replace("\n", "\n    ")
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
