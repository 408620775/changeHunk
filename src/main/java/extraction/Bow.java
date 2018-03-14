package extraction;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 词袋工具类，用于将一些源码信息转为词向量的形式，目前转义符后跟数字的情况还没考虑。
 * 将源码按照是注释区还是非注释区一块块的解析，拆分。例如是对于注释区的内容，注释中的大量*是不能当做乘法的。
 * 该类有三个主要的方法,分别为bow,bowP,bowPP(根据论文<Classifying Software Changes: Clean or
 * Buggy?>编写).
 *
 * @author niu
 */
public final class Bow {
    private static String[] dictory2 = {"!=", "==", "++", "--", "||", "&&",
            "<=", ">="};
    private static String[] dictory1 = {"=", "+", "-", "*", "/", "%", "!", "?"};
    private static String[] dictory3 = {"=", "!=", "+", "*", "-", "||", "/",
            "&", "%", "!", "?", ">=", "<=", "<", ">"}; // 去除注释中或者字符串中的特殊符号。

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
            if (subString.equals("")){
                continue;
            }
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
     *
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
     * @param text  源码内容
     * @param start 当前索引位置
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
     * Parse source code in hunk.
     *
     * @param text 源码内容
     * @return 转换后的词袋
     */
    public static Map<String, Integer> bowP(String text) {
        Map<String, Integer> bag = new HashMap<String, Integer>();
        if (text == null || text.length() == 0) {
            return bag;
        }
        List<Character> replaceChar = Arrays.asList('(', ')', '\'', '.', '\\', ';', ',', '"', ':', '[', ']', '{', '}',
                '/', '*', '<', '>','+','-','%','=','!','@','?','&');
        for (Character character : replaceChar) {
            text = text.replace(character, ' ');
        }
        String[] strings = text.split("\\s+");
        for (String subString : strings) {
            if (subString.equals("")) {
                continue;
            }
            if (subString.contains("++")) {
                String temp = "++";
                if (bag.keySet().contains(temp)) {
                    bag.put(temp, bag.get(temp) + 1);
                } else {
                    bag.put(temp, 1);
                }
                subString = subString.replace("++", "");
            }
            if (subString.equals("")) {
                continue;
            }
            if (bag.keySet().contains(subString)) {
                bag.put(subString, bag.get(subString) + 1);
            } else {
                bag.put(subString, 1);
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
     * @param oper   测试的操作符
     * @param string 测试的字符串
     * @param bag    要加入的bag
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
     *
     * @param string 要加入的单词
     * @param map    词袋
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
     *
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
                    if (temp.contains(".")){
                        temp = temp.substring(0,temp.indexOf('.')); //Delete file extension
                    }
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

