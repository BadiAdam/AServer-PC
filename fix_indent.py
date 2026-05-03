import re

files_to_process = [
    r"C:\Users\badie\Desktop\AServer-Desktop\AServer-Desktop\composeApp\src\jvmMain\kotlin\com\badiadam\aserver\ui\screens\FileManagerScreen.kt",
    r"C:\Users\badie\Desktop\AServer-Desktop\AServer-Desktop\composeApp\src\jvmMain\kotlin\com\badiadam\aserver\ui\screens\MyServersScreen.kt"
]

def process_file(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    # We want to find:
    # \nTooltipArea(\n([ \t]*)tooltip = \{\n
    # And replace with:
    # \n{spaces}TooltipArea(\n{original_spaces}tooltip = {\n
    # where {spaces} is {original_spaces} minus 4.
    
    def replacer(match):
        spaces_for_tooltip = match.group(1)
        if len(spaces_for_tooltip) >= 4:
            spaces_for_area = spaces_for_tooltip[:-4]
        else:
            spaces_for_area = ""
            
        return f"\n{spaces_for_area}TooltipArea(\n{spaces_for_tooltip}tooltip = {{\n"

    new_content = re.sub(r'\nTooltipArea\(\n([ \t]*)tooltip = \{\n', replacer, content)

    # Wait, what about the very first one? If there's no \n before TooltipArea (e.g. start of file)?
    # Unlikely, it's inside a Composable.
    
    with open(file_path, "w", encoding="utf-8") as f:
        f.write(new_content)

for fp in files_to_process:
    process_file(fp)
    print(f"Fixed {fp}")
