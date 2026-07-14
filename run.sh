clear
rm -rf out
mkdir out

javac -d out $(find src -name "*.java")
java -cp out Main