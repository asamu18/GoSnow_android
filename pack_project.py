import os

# 定义需要提取的文件后缀
TARGET_EXTENSIONS = {'.kt', '.kts', '.xml', '.java', '.properties', '.pro'}

# 定义需要忽略的文件夹
IGNORE_DIRS = {
    '.git', '.gradle', '.idea', 'build', 'captures', 'release', 
    'debug', 'test', 'androidTest', 'target'
}

# 定义需要忽略的具体文件名
IGNORE_FILES = {'local.properties'} # 包含敏感key的文件建议忽略

OUTPUT_FILE = 'project_full_context.txt'

def is_text_file(filename):
    return any(filename.endswith(ext) for ext in TARGET_EXTENSIONS)

def pack_code():
    project_root = os.getcwd()
    
    with open(OUTPUT_FILE, 'w', encoding='utf-8') as outfile:
        outfile.write(f"Project Structure for: {os.path.basename(project_root)}\n")
        outfile.write("="*50 + "\n\n")

        for root, dirs, files in os.walk(project_root):
            # 过滤忽略的文件夹
            dirs[:] = [d for d in dirs if d not in IGNORE_DIRS]
            
            for file in files:
                if file in IGNORE_FILES:
                    continue
                    
                if is_text_file(file):
                    file_path = os.path.join(root, file)
                    rel_path = os.path.relpath(file_path, project_root)
                    
                    try:
                        with open(file_path, 'r', encoding='utf-8') as infile:
                            content = infile.read()
                            
                            # 写入文件头分割线，方便AI识别
                            outfile.write(f"\n{'='*50}\n")
                            outfile.write(f"File: {rel_path}\n")
                            outfile.write(f"{'='*50}\n\n")
                            outfile.write(content)
                            outfile.write("\n")
                            
                        print(f"Packed: {rel_path}")
                    except Exception as e:
                        print(f"Skipped (Error): {rel_path} - {e}")

    print(f"\nDone! All code packed into: {OUTPUT_FILE}")

if __name__ == '__main__':
    pack_code()