package com.teacher.backend.util;

import java.util.*;
import com.teacher.backend.entity.CourseKnowledgePoint;

public class KnowledgePointUtils {
    /**
     * 获取指定知识点的所有上级（含自身，最顶层在前）
     */
    public static List<String> getAllAncestors(String pointName, List<CourseKnowledgePoint> allPoints) {
        Map<String, CourseKnowledgePoint> map = new HashMap<>();
        for (CourseKnowledgePoint p : allPoints) {
            map.put(p.getPointName(), p);
        }
        List<String> result = new ArrayList<>();
        String cur = pointName;
        while (cur != null && map.containsKey(cur)) {
            result.add(0, cur);
            String parent = map.get(cur).getParentPoint();
            cur = (parent == null || parent.isEmpty()) ? null : parent;
        }
        return result;
    }

    /**
     * 获取指定知识点的所有下级（含自身）
     */
    public static Set<String> getAllDescendants(String pointName, List<CourseKnowledgePoint> allPoints) {
        Set<String> result = new HashSet<>();
        result.add(pointName);
        Queue<String> queue = new LinkedList<>();
        queue.add(pointName);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            for (CourseKnowledgePoint p : allPoints) {
                if (cur.equals(p.getParentPoint())) {
                    result.add(p.getPointName());
                    queue.add(p.getPointName());
                }
            }
        }
        return result;
    }
}
