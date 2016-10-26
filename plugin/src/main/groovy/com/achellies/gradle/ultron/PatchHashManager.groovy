package com.achellies.gradle.ultron

import com.google.common.collect.Maps

class PatchHashManager {
    private static final String MAP_SEPARATOR = ":"

    final HashMap<String, String> hashMap;

    public PatchHashManager(File hashFile) {
        super();

        if (hashFile.exists() && hashFile.canRead()) {
            hashMap = parseHashMap(hashFile);
        }
    }

    public boolean isSame(String name, String hash) {
        def isSame = false;
        if (hashMap) {
            def value = hashMap.get(name)
            if (value) {
                if (value.equals(hash)) {
                    isSame = true
                }
            } else {
                isSame = false
            }
        }
        return isSame
    }

    private static HashMap<String, String> parseHashMap(File hashFile) {
        def hashMap = Maps.newHashMap();
        if (hashFile.exists()) {
            hashFile.eachLine {
                List list = it.split(MAP_SEPARATOR)
                if (list.size() == 2) {
                    hashMap.put(list[0], list[1])
                }
            }
        } else {
            println "$hashFile does not exist"
        }
        return hashMap
    }

    public static format(String path, String hash) {
        return path + MAP_SEPARATOR + hash + "\n"
    }
}
