import fnmatch
import os


def read_gitignore(directory):
    gitignore_path = os.path.join(directory, '.gitignore')
    if os.path.isfile(gitignore_path):
        with open(gitignore_path, 'r') as file:
            ignore_patterns = file.read().splitlines()
        return ignore_patterns
    return []


def is_ignored(file_path, ignore_patterns):
    # Normalize the file path to avoid issues with different path formats
    file_path = os.path.normpath(file_path)
    for pattern in ignore_patterns:
        # Ignore comments and empty lines in .gitignore
        if pattern.strip() == '' or pattern.startswith('#'):
            continue
        # Handle directory patterns in .gitignore
        if pattern.endswith('/'):
            if fnmatch.fnmatch(file_path + '/', '*' + pattern):
                return True
        else:
            if fnmatch.fnmatch(file_path, '*' + pattern):
                return True
    return False


def combine_files(directory, output_file, ignore_patterns):
    # Remove the output file if it already exists
    if os.path.exists(output_file):
        os.remove(output_file)

    with open(output_file, 'w', encoding='utf-8') as outfile:
        for root, dirs, files in os.walk(directory):
            # Exclude ignored directories and the .git directory
            dirs[:] = [d for d in dirs if not is_ignored(os.path.join(root, d), ignore_patterns) and d != '.git']

            for file in files:
                file_path = os.path.join(root, file)
                if not is_ignored(file_path, ignore_patterns):
                    try:
                        with open(file_path, 'r', encoding='utf-8') as infile:
                            outfile.write(f"File: {file_path}\n")
                            outfile.write(infile.read())
                            outfile.write("\n\n")
                    except UnicodeDecodeError:
                        print(f"Skipping file: {file_path} (Unicode decode error)")
                    except Exception as e:
                        print(f"Error processing file: {file_path}")
                        print(f"Error message: {str(e)}")


# Directory to scan
directory = '.'

# Output file name
output_file = 'combined_files.txt'

# Read ignore patterns from .gitignore file
ignore_patterns = read_gitignore(directory)

# Call the function to combine files
combine_files(directory, output_file, ignore_patterns)
