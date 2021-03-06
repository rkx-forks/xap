package com.gigaspaces.internal.server.space.tiered_storage;

import com.gigaspaces.internal.metadata.EntryType;
import com.gigaspaces.internal.metadata.ITypeDesc;
import com.gigaspaces.internal.metadata.PropertyInfo;
import com.gigaspaces.internal.server.metadata.IServerTypeDesc;
import com.gigaspaces.internal.server.space.SpaceEngine;
import com.gigaspaces.internal.server.space.SpaceUidFactory;
import com.gigaspaces.internal.server.storage.*;
import com.j_spaces.core.cache.context.Context;
import com.j_spaces.core.cache.context.TemplateMatchTier;
import com.j_spaces.core.cache.context.TieredState;
import com.j_spaces.core.sadapter.SAException;
import net.jini.core.lease.Lease;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static com.gigaspaces.internal.server.space.tiered_storage.SqliteUtils.getPropertyValue;

public class TieredStorageUtils {
    private static Logger logger = LoggerFactory.getLogger(TieredStorageUtils.class);

    public static Map<Object, EntryTieredMetaData> getEntriesTieredMetaDataByIds(SpaceEngine space, String typeName, Object[] ids) throws Exception {
        Map<Object, EntryTieredMetaData> entryTieredMetaDataMap = new HashMap<>();
        if (!space.isTieredStorage()) {
            throw new Exception("Tiered storage undefined");
        }
        Context context = null;
        try {
            context = space.getCacheManager().getCacheContext();
            for (Object id : ids) {
                entryTieredMetaDataMap.put(id, getEntryTieredMetaDataById(space, typeName, id, context));
            }
        } finally {
            space.getCacheManager().freeCacheContext(context);
        }
        return entryTieredMetaDataMap;
    }

    private static EntryTieredMetaData getEntryTieredMetaDataById(SpaceEngine space, String typeName, Object id, Context context) {
        EntryTieredMetaData entryTieredMetaData = new EntryTieredMetaData();
        IServerTypeDesc typeDesc = space.getTypeManager().getServerTypeDesc(typeName);
        IEntryHolder hotEntryHolder;
        if (typeDesc.getTypeDesc().isAutoGenerateId()) {
            hotEntryHolder = space.getCacheManager().getEntryByUidFromPureCache(((String) id));
        } else {
            hotEntryHolder = space.getCacheManager().getEntryByIdFromPureCache(id, typeDesc);
        }
        IEntryHolder coldEntryHolder = null;

        try {
            if (typeDesc.getTypeDesc().isAutoGenerateId()) {
                coldEntryHolder = space.getTieredStorageManager().getInternalStorage().getEntryByUID(context, typeDesc.getTypeName(), (String) id);
            }else {
                coldEntryHolder = space.getTieredStorageManager().getInternalStorage().getEntryById(context, typeDesc.getTypeName(), id);
            }
        } catch (SAException e) { //entry doesn't exist in cold tier
        }

        if (hotEntryHolder != null) {
            if (coldEntryHolder == null) {
                entryTieredMetaData.setTieredState(TieredState.TIERED_HOT);
            } else {
                entryTieredMetaData.setTieredState(TieredState.TIERED_HOT_AND_COLD);
                entryTieredMetaData.setIdenticalToCache(isIdenticalToCache(typeDesc.getTypeDesc(), hotEntryHolder, (coldEntryHolder)));
            }
        } else {
            if (coldEntryHolder != null) {
                entryTieredMetaData.setTieredState(TieredState.TIERED_COLD);
            } //else- entry doesn't exist
        }
        return entryTieredMetaData;
    }

    private static boolean isIdenticalToCache(ITypeDesc typeDesc, IEntryHolder hotEntryHolder, IEntryHolder coldEntryHolder) {
        IEntryData hotEntry = hotEntryHolder.getEntryData();
        IEntryData coldEntry = coldEntryHolder.getEntryData();
        if (hotEntry.getNumOfFixedProperties() != coldEntry.getNumOfFixedProperties()) {
            return false;
        }
        for (int i = 0; i < hotEntry.getNumOfFixedProperties(); ++i) {
            Object hotValue;
            Object coldValue;
            if (typeDesc.isAutoGenerateId() && ((PropertyInfo) typeDesc.getFixedProperty(typeDesc.getIdPropertyName())).getOriginalIndex() == i) {
                hotValue = hotEntryHolder.getUID();
                coldValue = coldEntryHolder.getUID();
            } else {
                hotValue = hotEntry.getFixedPropertiesValues()[i];
                coldValue = coldEntry.getFixedPropertiesValues()[i];
            }
            if (hotValue == null || coldValue == null) {
                return hotValue == coldValue;
            }
            if(!hotValue.equals(coldValue)){
                logger.warn("Failed to have consistency between hot and cold tier for id: " +
                        hotEntry.getEntryDataType().name() + " Hot: " + hotValue + " Cold: " + coldValue);

                return false;
            }
        }
        return true;
    }

    public static List<String> getTiersAsList(TemplateMatchTier templateTieredState) {
        switch (templateTieredState) {
            case MATCH_HOT:
                return Collections.singletonList("HOT");
            case MATCH_COLD:
                return Collections.singletonList("COLD");
            case MATCH_HOT_AND_COLD:
                return Arrays.asList("HOT", "COLD");
        }

        throw new IllegalStateException("Should be unreachable");
    }

    public static IEntryHolder getEntryHolderFromRow(IServerTypeDesc serverTypeDesc, ResultSet resultSet) throws SQLException {
        ITypeDesc typeDesc = serverTypeDesc.getTypeDesc();
        PropertyInfo[] properties = typeDesc.getProperties();
        Object[] values = new Object[properties.length];
        for (int i = 0; i < properties.length; i++) {
            values[i] = getPropertyValue(resultSet, properties[i]);
        }
        FlatEntryData data = new FlatEntryData(values, null, typeDesc.getEntryTypeDesc(EntryType.DOCUMENT_JAVA), 0, Lease.FOREVER, null);
        String uid;
        if (typeDesc.isAutoGenerateId()) {
            uid = (String) data.getFixedPropertyValue(((PropertyInfo) typeDesc.getFixedProperty(typeDesc.getIdPropertyName())).getOriginalIndex());
        } else {
            Object idFromEntry = data.getFixedPropertyValue(((PropertyInfo) typeDesc.getFixedProperty(typeDesc.getIdPropertyName())).getOriginalIndex());
            uid = SpaceUidFactory.createUidFromTypeAndId(typeDesc, idFromEntry);
        }
        return new EntryHolder(serverTypeDesc, uid, 0, false, data);
    }
}
