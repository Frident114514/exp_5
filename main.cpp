#include <iostream>
#include <sstream>
#include <string>

int main() {
    // 两位同学练习冲突时，都修改下一行的提示语，再分别提交。
    const std::string prompt1 = "这是一个提示语，";
    const std::string prompt = "请输入一句话：";
    std::cout << prompt1 << prompt;

    std::string line;
    std::getline(std::cin, line);
    std::istringstream input(line);
    std::string word;
    int count = 0;
    while (input >> word) {
        ++count;
    }
    std::cout << "单词数：" << count << '\n';
    return 0;
}
