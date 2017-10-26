package main.extraction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public final class Merge {


    /**
     * 将多个contentMap根据其commit_id和file_id合并,但是这个函数感觉空间复杂度很高.
     * @param list
     * @return
     * @throws Exception
     */
    public static Map<List<Integer>, StringBuffer> mergeMap(List<Map<List<Integer>, StringBuffer>> list) throws Exception {
        if (list.size()==0) {
            throw new Exception("the list size can't be 0!");
        }
        System.out.println("Merge");
        Map<List<Integer>, StringBuffer> resMap=new LinkedHashMap<>();
        for (List<Integer> key: list.get(0).keySet()) {
            resMap.put(key, new StringBuffer());
        }
        for (List<Integer> keyList: resMap.keySet()) {
            for (Map<List<Integer>, StringBuffer> part : list) {
                resMap.get(keyList).append(part.get(keyList));
            }
            if (resMap.get(keyList).charAt(resMap.get(keyList).length()-1)==',') {
                resMap.get(keyList).deleteCharAt(resMap.get(keyList).length()-1);
            }
        }
        return resMap;
    }
}

