package org.sunbird.common.cacheloader;

import org.apache.commons.collections.CollectionUtils;
import org.sunbird.cache.CacheFactory;
import org.sunbird.cache.interfaces.Cache;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerUtil;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.util.DataCacheHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PageCacheLoaderService implements Runnable {
  private CassandraOperation cassandraOperation = ServiceFactory.getInstance();
  private static boolean isCacheEnabled = false;
  private static LoggerUtil logger = new LoggerUtil(PageCacheLoaderService.class);
  //      Boolean.parseBoolean(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_CACHE_ENABLE));

  private static Cache cache = CacheFactory.getInstance();

  @SuppressWarnings("unchecked")
  public Map<String, Map<String, Object>> cacheLoader(String tableName) {
    Map<String, Map<String, Object>> map = new HashMap<>();
    try {
      Response response = cassandraOperation.getAllRecords(null, ProjectUtil.getConfigValue(JsonKey.SUNBIRD_KEYSPACE), tableName);
      List<Map<String, Object>> responseList =
          (List<Map<String, Object>>) response.get(JsonKey.RESPONSE);
      if (CollectionUtils.isNotEmpty(responseList)) {
        if (tableName.equalsIgnoreCase(JsonKey.PAGE_SECTION)) {
          loadPageSectionInCache(responseList, map);
        } else if (tableName.equalsIgnoreCase(JsonKey.PAGE_MANAGEMENT)) {
          loadPagesInCache(responseList, map);
        }
      }
    } catch (Exception e) {
      logger.error(null, "CacheLoaderService:cacheLoader: Exception occurred = " + e.getMessage(), e);
    }
    return map;
  }

  void loadPageSectionInCache(
      List<Map<String, Object>> responseList, Map<String, Map<String, Object>> map) {

    for (Map<String, Object> resultMap : responseList) {
      removeUnwantedData(resultMap, "");
      map.put((String) resultMap.get(JsonKey.ID), resultMap);
    }
  }

  void loadPagesInCache(
      List<Map<String, Object>> responseList, Map<String, Map<String, Object>> map) {

    for (Map<String, Object> resultMap : responseList) {
      String pageName = (String) resultMap.get(JsonKey.PAGE_NAME);
      String orgId = (String) resultMap.get(JsonKey.ORGANISATION_ID);
      if (orgId == null) {
        orgId = "NA";
      }
      map.put(orgId + ":" + pageName, resultMap);
    }
  }

  @Override
  public void run() {
    if (isCacheEnabled) {
      updateAllCache();
    }
  }

  private void updateAllCache() {
    logger.info(null, "CacheLoaderService: updateAllCache called");
    updateCache(cacheLoader(JsonKey.PAGE_SECTION), ActorOperations.GET_SECTION.getValue());
    updateCache(cacheLoader(JsonKey.PAGE_MANAGEMENT), ActorOperations.GET_PAGE_DATA.getValue());
  }

  private void removeUnwantedData(Map<String, Object> map, String from) {
    map.remove(JsonKey.CREATED_DATE);
    map.remove(JsonKey.CREATED_BY);
    map.remove(JsonKey.UPDATED_DATE);
    map.remove(JsonKey.UPDATED_BY);
    if ("getPageData".equalsIgnoreCase(from)) {
      map.remove(JsonKey.STATUS);
    }
  }

  private static void updateCache(Map<String, Map<String, Object>> cacheMap, String mapName) {
    try {
      Set<String> keys = cacheMap.keySet();
      for (String key : keys) {
        cache.put(mapName, key, cacheMap.get(key));
      }
    } catch (Exception e) {
      logger.error(null, "CacheLoaderService:updateCache: Error occured = " + e.getMessage(), e);
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> T getDataFromCache(String mapName, String key, Class<T> class1) {
    if (isCacheEnabled) {
      Object res = cache.get(mapName, key, class1);
      if (res != null) {
        return (T) res;
      }
    } else {
      Map<String, Map<String, Object>> map = getDCMap(mapName);
      if (map != null) {
        return (T) map.get(key);
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  public static boolean putDataIntoCache(String mapName, String key, Object obj) {
    if (isCacheEnabled) {
      cache.put(mapName, key, obj);
      return true;
    } else {
      Map<String, Map<String, Object>> map = getDCMap(mapName);
      if (map != null) {
        map.put(key, (Map<String, Object>) obj);
      }
    }
    return false;
  }

  private static Map<String, Map<String, Object>> getDCMap(String mapName) {
    switch (mapName) {
      case "getPageData":
        return DataCacheHandler.getPageMap();
      case "getSection":
        return DataCacheHandler.getSectionMap();
    }
    return null;
  }
}
