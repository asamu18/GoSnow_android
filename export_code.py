import os

# ================= 配置区域 =================

# 输出文件的名称
OUTPUT_FILE = 'project_context.txt'

# 需要忽略的文件夹 (完全匹配)
IGNORE_DIRS = {
    '.git', '.idea', '.vscode', '__pycache__', 'node_modules', 
    'venv', 'env', 'build', 'dist', 'target', 'bin', 'obj',
    'coverage', '.next', '.nuxt'
}

# 需要忽略的文件 (完全匹配)
IGNORE_FILES = {
    'package-lock.json', 'yarn.lock', 'pnpm-lock.yaml', 
    'Cargo.lock', '.DS_Store', 'export_code.py'
}

# 指定需要抓取的文件后缀 (如果不设置，脚本会尝试读取所有文本文件，但可能会读取到杂乱文件)
# 如果你想抓取所有文件，可以将此变量设为 None
ALLOWED_EXTENSIONS = {
    '.py', '.js', '.jsx', '.ts', '.tsx', '.java', '.c', '.cpp', '.h', '.hpp',
    '.go', '.rs', '.php', '.rb', '.sh', '.sql', '.html', '.css', '.scss',
    '.json', '.yaml', '.yml', '.md', '.toml', '.xml', '.vue', '.swift', '.kt'
}

# ===========================================

def is_text_file(file_path):
    """简单的二进制文件检测"""
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            f.read(1024)
            return True
    except UnicodeDecodeError:
        return False
    except Exception:
        return False

def main():
    root_dir = os.getcwd() # 获取当前目录
    
    with open(OUTPUT_FILE, 'w', encoding='utf-8') as out_f:
        # 写入头部信息
        out_f.write(f"Project Scan Root: {root_dir}\n")
        out_f.write("==================================================\n\n")

        file_count = 0

        for root, dirs, files in os.walk(root_dir):
            # 1. 修改 dirs 列表以原地过滤文件夹 (os.walk 的特性)
            dirs[:] = [d for d in dirs if d not in IGNORE_DIRS and not d.startswith('.')]

            for file in files:
                if file in IGNORE_FILES:
                    continue
                
                file_path = os.path.join(root, file)
                rel_path = os.path.relpath(file_path, root_dir)
                _, ext = os.path.splitext(file)

                # 2. 后缀名过滤
                if ALLOWED_EXTENSIONS is not None:
                    if ext not in ALLOWED_EXTENSIONS:
                        continue

                # 3. 这里的检查防止读取到非文本的允许后缀（极其罕见的情况）
                if not is_text_file(file_path):
                    continue

                try:
                    with open(file_path, 'r', encoding='utf-8') as f:
                        content = f.read()
                        
                    # 4. 格式化输出
                    out_f.write(f"File: {rel_path}\n")
                    out_f.write("--------------------------------------------------\n")
                    out_f.write(content)
                    out_f.write("\n\n==================================================\n\n")
                    
                    print(f"已处理: {rel_path}")
                    file_count += 1
                    
                except Exception as e:
                    print(f"读取文件出错 {rel_path}: {e}")

    print(f"\n完成！共处理 {file_count} 个文件。")
    print(f"结果已保存至: {OUTPUT_FILE}")

if __name__ == "__main__":
    main()