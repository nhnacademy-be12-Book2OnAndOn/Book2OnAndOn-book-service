import os
import re
import sys

def remove_unused_imports(file_path):
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            lines = f.readlines()
    except UnicodeDecodeError:
        # Fallback to other encoding if necessary
        try:
            with open(file_path, 'r', encoding='cp949') as f:
                lines = f.readlines()
        except:
            return False, []

    content = "".join(lines)
    
    import_pattern = re.compile(r'^import\s+(?:static\s+)?([\w\.]+)\s*;')
    
    import_lines_indices = []
    for i, line in enumerate(lines):
        if import_pattern.match(line.strip()):
            import_lines_indices.append(i)
            
    if not import_lines_indices:
        return False, []
    
    # Content without any import lines
    content_no_imports = "".join([line for i, line in enumerate(lines) if i not in import_lines_indices])
    
    # Strip comments and strings from content_no_imports for usage checking
    # Multi-line comments
    clean_no_imports = re.sub(r'/\*.*?\*/', '', content_no_imports, flags=re.DOTALL)
    # Single-line comments
    clean_no_imports = re.sub(r'//.*', '', clean_no_imports)
    # Strings
    clean_no_imports = re.sub(r'"(?:\\.|[^"\\])*"', '""', clean_no_imports)
    # Character literals
    clean_no_imports = re.sub(r"'(?:\\.|[^'\\])'", "''", clean_no_imports)

    to_remove = set()
    removed_imports = []
    
    for i in import_lines_indices:
        line = lines[i].strip()
        match = import_pattern.match(line)
        if match:
            full_import = match.group(1)
            if '*' in full_import:
                continue
            
            simple_name = full_import.split('.')[-1]
            
            # Search for simple_name in clean_no_imports
            if not re.search(r'\b' + re.escape(simple_name) + r'\b', clean_no_imports):
                to_remove.add(i)
                removed_imports.append(line)

    if not to_remove:
        return False, []

    new_lines = [line for i, line in enumerate(lines) if i not in to_remove]
    
    with open(file_path, 'w', encoding='utf-8') as f:
        f.writelines(new_lines)
    
    return True, removed_imports

def main():
    src_dirs = ['src/main/java', 'src/test/java']
    modified_files = []
    total_removed = 0

    for src_dir in src_dirs:
        if not os.path.exists(src_dir):
            continue
        for root, dirs, files in os.walk(src_dir):
            for file in files:
                if file.endswith('.java'):
                    file_path = os.path.join(root, file)
                    modified, removed = remove_unused_imports(file_path)
                    if modified:
                        modified_files.append((file_path, removed))
                        total_removed += len(removed)

    if modified_files:
        print(f"Modified {len(modified_files)} files, removed {total_removed} imports.")
        for path, removed in modified_files:
            print(f"\nFile: {path}")
            for r in removed:
                print(f"  Removed: {r}")
    else:
        print("No unused imports found.")

if __name__ == "__main__":
    main()
