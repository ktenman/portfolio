rm -rf .gradle
rm -rf build
rm -rf .idea
rm -rf *.iml
rm -rf node_modules
rm -rf .kotlin
rm gradlew
rm LICENSE
rm package-lock.json
rm combined_files.txt
python3 code.py
git reset --hard
npm install
