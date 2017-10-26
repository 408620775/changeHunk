package main.extraction;

import java.util.HashMap;
import java.util.Map;

/**
 * 词袋工具类，用于将一些源码信息转为词向量的形式，目前转义符后跟数字的情况还没考虑。
 * 将源码按照是注释区还是非注释区一块块的解析，拆分。例如是对于注释区的内容，注释中的大量*是不能当做乘法的。
 * 该类有三个主要的方法,分别为bow,bowP,bowPP(根据论文<Classifying Software Changes: Clean or
 * Buggy?>编写).
 *
 * @author niu
 *
 */
public final class Bow {
    private static String[] dictory2 = { "!=", "==", "++", "--", "||", "&&",
            "<=", ">=" };
    private static String[] dictory1 = { "=", "+", "-", "*", "/", "%", "!", "?" };
    private static String[] dictory3 = { "=", "!=", "+", "*", "-", "||", "/",
            "&", "%", "!", "?", ">=", "<=", "<", ">" }; // 去除注释中或者字符串中的特殊符号。

    /**
     * 禁止实例化
     */
    private Bow() {

    }

    /**
     * 根据changelog信息,获取对应文本的词向量,识别的词向量中不包括特殊符号和数字.
     *
     * @param text 输入的文本信息.
     * @return 文本所对应的词向量.
     */
    public static Map<String, Integer> bow(String text) {
        Map<String, Integer> bag = new HashMap<String, Integer>();
        int startIndex = 0;
        int endIndex = 0;
        while (endIndex <= text.length() - 1) {
            while (endIndex <= text.length() - 1
                    && (!isCharacter(text.charAt(endIndex)))) {
                endIndex++;
            }
            startIndex = endIndex;
            while ((endIndex <= text.length() - 1)
                    && isCharacter(text.charAt(endIndex))) {
                endIndex++;
            }
            String subString = text.substring(startIndex, endIndex);
            subString = subString.toLowerCase();
            if (bag.keySet().contains(subString)) {
                bag.put(subString, bag.get(subString) + 1);
            } else {
                bag.put(subString, 1);
            }
        }
        return bag;
    }

    /**
     * 判断字符c是否为字母,其实这个函数在库函数中已经实现了.
     * @param c
     * @return
     */
    private static boolean isCharacter(char c) {
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
            return true;
        }
        return false;
    }

    /**
     * 获取从当前位置开始text第一次出现注释或者字符串的地方。
     *
     * @param text
     *            源码内容
     * @param start
     *            当前索引位置
     * @return 下一次出现注释或者字符串的地方
     */
    public static int getIndex(StringBuffer text, int start) {
        while (start < text.length()) {
            if (start < text.length() - 1
                    && text.substring(start, start + 2).equals("/*")) {
                break;
            }
            if (start < text.length() - 1 && start > 1
                    && text.charAt(start) == '"'
                    && text.charAt(start - 1) == '\''
                    && text.charAt(start + 1) == '\'') {
                start++;
                continue;
            }
            if ((start > 1 && text.charAt(start) == '"' && text
                    .charAt(start - 1) != '\\')
                    || (start > 2 && text.substring(start - 2, start + 1)
                    .equals("\\\\\""))
                    || (start == 1 && text.charAt(start) == '"')) {
                break;
            }
            if (start < text.length() - 1
                    && text.substring(start, start + 2).equals("//")) {
                break;
            }
            start++;
        }
        return start;
    }

    /**
     * 将复杂的带有注释的源码文件转为词袋形式。
     *
     * @param text
     *            源码内容
     * @return 转换后的词袋
     */
    public static Map<String, Integer> bowP(StringBuffer text) {
        StringBuffer hunkBuffer = new StringBuffer();
        Map<String, Integer> bag = new HashMap<String, Integer>();
        @SuppressWarnings("unused")
        int i = 1;
        while (text.toString().length() > 0) {// 将源码区内容直接加入hunkBuffer，将注释区内容处理后加入hunkBuffer。
            int start = 0;
            start = getIndex(text, start);
            if (start == text.length()) { // 如果start直接找到了文件末尾，则说明上面没有注释区。
                hunkBuffer.append(" " + text.substring(0, start));
                break;
            }
            while (text.charAt(start) == '"') { // 如果找到的第一个注释类字符为“，如果其前面由\那么就不是真正的注释区的开始。
                if (start > 0 && text.charAt(start - 1) == '\\') { // 不是一个字符串真正的开始或者结束。
                    start++;
                    start = getIndex(text, start);
                } else {
                    break;
                }
            }
            // 执行到此处要么到了文档末尾，要么必然找到了注释区的开始。
            if (start == text.length() - 1) { // 整个文档搜索到了最后一个字符，那么就直接退出。
                hunkBuffer.append(text);
                break;
            }
            // 第一块注释区前面的都是源码内容，加入hunkBuffer，但是似乎不需要加这个空格，如果加了，下面的分裂应以一个或多个空格分。
            hunkBuffer.append(" " + text.substring(0, start));
            text.delete(0, start); // 将text中处理完的内容删掉
            start = 0; // 将指针指向text头部。
            String startOper = new String();
            if (text.charAt(start) == '/') { // 匹配之前出现的操作符，确定注释区。
                if (text.charAt(start + 1) == '*') {
                    startOper = "/*";
                } else {
                    startOper = "//";
                }
            } else {
                startOper = "\"";
            }
            String rage;

            if (startOper.equals("//")) { // 确定当前注释区范围，处理掉双斜杠，用removeSC2方法处理注释区内容。
                int inedex = text.indexOf("\n");
                if (inedex == -1) { // 最后一样的注释
                    rage = text.substring(start + 2, text.length());
                    text = null;
                } else { // 之后还有内容
                    rage = text.substring(start + 2, text.indexOf("\n"));
                    text.delete(0, text.indexOf("\n") + 1);
                }
                hunkBuffer.append(" " + removeSC2(rage));
            } else if (startOper.equals("/*")) {
                rage = text.substring(start + 2, text.substring(2)
                        .indexOf("*/") + 2);
                hunkBuffer.append(" " + removeSC2(rage));
                text.delete(0, text.substring(2).indexOf("*/") + 4);
            } else {
                text.deleteCharAt(0);
                int tail = text.indexOf("\"");
                while (tail >= 1) {
                    int numl = 0;
                    for (int j = tail - 1; j >= 0; j--) {
                        if (text.charAt(j) == '\\') {
                            numl++;
                        } else {
                            break;
                        }
                    }
                    if (numl % 2 == 0) {
                        break;
                    } else {
                        tail = tail + 1;
                        tail = tail
                                + text.substring(tail, text.length()).indexOf(
                                '"');
                    }
                }
                rage = text.substring(0, tail);
                i++;
                hunkBuffer.append(" " + removeSC2(rage));
                text.delete(0, tail + 1);
            }
        }

        String dirList[] = hunkBuffer.toString().split(
                "[\\.\\s\\)\\(;:,\"\\[\\]\\{\\}]|//]");
        for (String string : dirList) {
            if (!string.equals("")) { // 这句话是不是也可以优化？
                boolean contain = false;
                for (String oper : dictory2) {
                    contain = diviOper(oper, string, bag);
                    if (contain == true) {
                        break;
                    }
                }
                if (contain == false) {
                    for (String oper2 : dictory1) {
                        contain = diviOper(oper2, string, bag);
                        if (contain == true) {
                            break;
                        }
                    }
                }
                if (contain == false) {
                    // <>不是操作符的情况
                    if (string.contains("<") || string.contains(">")) {
                        String[] divTempStrings = string.split(">|<");
                        for (String string2 : divTempStrings) {
                            if (!string2.equals("")) {
                                putInBag(string2, bag);
                            }
                        }
                    } else {
                        putInBag(string, bag);
                    }
                }
            }
        }
        return bag;
    }

    private static String removeSC2(String rage) {
        for (String string : dictory3) {
            rage = rage.replace(string, " ");
        }
        return rage;
    }

    /**
     * 判定字符串中是否包含着操作符，如果包含或者本身自己就是操作符则将二者分开，然后分别加入bag。
     * 需要注意的是如果一个字符串中包含了<，或者>，不认为这是操作符，以便和java中使用很多的<区分，
     * 就是说，默认如果是减号的话一定没有和变量夹杂在一起
     *
     * @param oper
     *            测试的操作符
     * @param string
     *            测试的字符串
     * @param bag
     *            要加入的bag
     * @return 是否包含操作符或者本身是操作符
     */
    public static boolean diviOper(String oper, String string,
                                   Map<String, Integer> bag) {
        if (string.equals(oper)) {
            putInBag(string, bag);
            return true;
        }
        String diOperString = oper;
        if (string.contains(oper)) {
            if (oper.equals("++")) {
                diOperString = "\\+\\+";
            } else if (oper.equals("+")) {
                diOperString = "\\+";
            } else if (oper.equals("?")) {
                diOperString = "\\?";
            } else if (oper.equals("*")) {
                diOperString = "\\*";
            }
            String[] divide1 = string.split(diOperString);
            for (String string2 : divide1) {
                if (!string2.equals("")) {
                    putInBag(string2, bag);
                }
            }
            putInBag(oper, bag);
            return true;
        }
        return false;
    }

    /**
     * 将单词string加入词袋中
     * @param string 要加入的单词
     * @param map 词袋
     */
    private static void putInBag(String string, Map<String, Integer> map) {
        if (map.containsKey(string)) {
            map.put(string, map.get(string) + 1);
        } else {
            map.put(string, 1);
        }
    }

    /**
     * 將文件路徑解析成词袋形式.
     * @param text
     * @return
     */
    public static Map<String, Integer> bowPP(String text) {
        Map<String, Integer> bag = new HashMap<>();
        String dirList[] = text.split("/");
        String regex = ".*[A-Z].*";
        for (String string : dirList) {
            if (string.matches(regex)) {
                int startIndex = 0;
                int endIndex = 1;
                while (endIndex < string.length()) {
                    while (endIndex < string.length()
                            && (!Character.isUpperCase(string.charAt(endIndex)))) {
                        endIndex++;
                    }
                    String temp = string.substring(startIndex, endIndex)
                            .toLowerCase(); // 之前没有处理大小写转换问题。
                    if (bag.keySet().contains(temp)) {
                        bag.put(temp, bag.get(temp) + 1);
                    } else {
                        bag.put(temp, 1);
                    }
                    startIndex = endIndex;
                    endIndex = endIndex + 1;
                    if (endIndex >= string.length()
                            && startIndex < string.length()) {
                        if (bag.keySet().contains(string.charAt(startIndex))) {
                            bag.put(string.charAt(startIndex) + "",
                                    bag.get(string.charAt(startIndex) + "") + 1);
                        } else {
                            bag.put(string.charAt(startIndex) + "", 1);
                        }
                    }
                }
            } else {
                if (bag.keySet().contains(string)) {
                    bag.put(string, bag.get(string) + 1);
                } else {
                    bag.put(string, 1);
                }
            }
        }
        return bag;
    }
}

