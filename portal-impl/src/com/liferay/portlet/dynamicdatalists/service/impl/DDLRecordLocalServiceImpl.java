/**
 * Copyright (c) 2000-2011 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.portlet.dynamicdatalists.service.impl;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.OrderByComparator;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.kernel.workflow.WorkflowHandlerRegistryUtil;
import com.liferay.portal.model.User;
import com.liferay.portal.service.ServiceContext;
import com.liferay.portal.service.ServiceContextUtil;
import com.liferay.portlet.documentlibrary.util.DLUtil;
import com.liferay.portlet.dynamicdatalists.NoSuchRecordVersionException;
import com.liferay.portlet.dynamicdatalists.model.DDLRecord;
import com.liferay.portlet.dynamicdatalists.model.DDLRecordConstants;
import com.liferay.portlet.dynamicdatalists.model.DDLRecordSet;
import com.liferay.portlet.dynamicdatalists.model.DDLRecordVersion;
import com.liferay.portlet.dynamicdatalists.service.base.DDLRecordLocalServiceBaseImpl;
import com.liferay.portlet.dynamicdatalists.util.comparator.DDLRecordVersionVersionComparator;
import com.liferay.portlet.dynamicdatamapping.model.DDMStructure;
import com.liferay.portlet.dynamicdatamapping.storage.Field;
import com.liferay.portlet.dynamicdatamapping.storage.Fields;
import com.liferay.portlet.dynamicdatamapping.storage.StorageEngineUtil;

import java.io.Serializable;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @author Marcellus Tavares
 * @author Eduardo Lundgren
 */
public class DDLRecordLocalServiceImpl
	extends DDLRecordLocalServiceBaseImpl {

	public DDLRecord addRecord(
			long userId, long groupId, long recordSetId, int displayIndex,
			Fields fields, ServiceContext serviceContext)
		throws PortalException, SystemException {

		// Record

		User user = userPersistence.findByPrimaryKey(userId);
		Date now = new Date();

		DDLRecordSet recordSet = ddlRecordSetPersistence.findByPrimaryKey(
			recordSetId);

		long recordId = counterLocalService.increment();

		DDLRecord record = ddlRecordPersistence.create(recordId);

		record.setUuid(serviceContext.getUuid());
		record.setGroupId(groupId);
		record.setCompanyId(user.getCompanyId());
		record.setUserId(user.getUserId());
		record.setUserName(user.getFullName());
		record.setVersionUserId(user.getUserId());
		record.setVersionUserName(user.getFullName());
		record.setCreateDate(serviceContext.getCreateDate(now));
		record.setModifiedDate(serviceContext.getModifiedDate(now));

		DDMStructure ddmStructure = recordSet.getDDMStructure();

		record.setClassNameId(ddmStructure.getClassNameId());

		long classPK = StorageEngineUtil.create(
			recordSet.getCompanyId(), recordSet.getDDMStructureId(), fields,
			serviceContext);

		record.setClassPK(classPK);

		record.setRecordSetId(recordSetId);
		record.setVersion(DDLRecordConstants.DEFAULT_VERSION);
		record.setDisplayIndex(displayIndex);

		ddlRecordPersistence.update(record, false);

		// Record version

		DDLRecordVersion recordVersion = addRecordVersion(
			user, record, classPK, DDLRecordConstants.DEFAULT_VERSION,
			displayIndex, WorkflowConstants.STATUS_DRAFT);

		// Asset

		Locale locale = ServiceContextUtil.getLocale(serviceContext);

		updateAsset(
			userId, record, recordVersion, serviceContext.getAssetCategoryIds(),
			serviceContext.getAssetTagNames(),
			locale);

		// Workflow

		WorkflowHandlerRegistryUtil.startWorkflowInstance(
			user.getCompanyId(), groupId, userId, DDLRecord.class.getName(),
			record.getRecordId(), record, serviceContext);

		return record;
	}

	public DDLRecord addRecord(
			long userId, long groupId, long recordSetId,
			int displayIndex, Map<String, Serializable> fieldsMap,
			ServiceContext serviceContext)
		throws PortalException, SystemException {

		Fields fields = toFields(fieldsMap);

		return addRecord(
			userId, groupId, recordSetId, displayIndex, fields, serviceContext);
	}

	public void deleteRecord(DDLRecord record)
		throws PortalException, SystemException {

		// Record

		ddlRecordPersistence.remove(record);

		// Record Versions

		List<DDLRecordVersion> recordVersions =
			ddlRecordVersionPersistence.findByRecordId(record.getRecordId());

		for (DDLRecordVersion recordVersion : recordVersions) {
			ddlRecordVersionPersistence.remove(recordVersion);

			// Dynamic data mapping storage

			StorageEngineUtil.deleteByClass(recordVersion.getClassPK());
		}

		// Workflow

		workflowInstanceLinkLocalService.deleteWorkflowInstanceLinks(
			record.getCompanyId(), record.getGroupId(),
			DDLRecord.class.getName(), record.getRecordId());
	}

	public void deleteRecord(long recordId)
		throws PortalException, SystemException {

		DDLRecord record = ddlRecordPersistence.findByPrimaryKey(
			recordId);

		deleteRecord(record);
	}

	public void deleteRecords(long recordSetId)
		throws PortalException, SystemException {

		List<DDLRecord> records = ddlRecordPersistence.findByRecordSetId(
			recordSetId);

		for (DDLRecord record : records) {
			deleteRecord(record);
		}
	}

	public DDLRecordVersion getLatestRecordVersion(long recordId)
		throws PortalException, SystemException {

		List<DDLRecordVersion> recordVersions =
			ddlRecordVersionPersistence.findByRecordId(recordId);

		if (recordVersions.isEmpty()) {
			throw new NoSuchRecordVersionException();
		}

		recordVersions = ListUtil.copy(recordVersions);

		Collections.sort(
			recordVersions, new DDLRecordVersionVersionComparator());

		return recordVersions.get(0);
	}

	public DDLRecord getRecord(long recordId)
		throws PortalException, SystemException {

		return ddlRecordPersistence.findByPrimaryKey(recordId);
	}

	public List<DDLRecord> getRecords(long recordSetId)
		throws SystemException {

		return ddlRecordPersistence.findByRecordSetId(recordSetId);
	}

	public List<DDLRecord> getRecords(
			long recordSetId, int status, int start, int end,
			OrderByComparator orderByComparator)
		throws SystemException {

		return ddlRecordFinder.findByR_S(
			recordSetId, status, start, end, orderByComparator);
	}

	public int getRecordsCount(long recordSetId, int status)
		throws SystemException {

		return ddlRecordFinder.countByR_S(recordSetId, status);
	}

	public void updateAsset(
			long userId, DDLRecord record, DDLRecordVersion recordVersion,
			long[] assetCategoryIds, String[] assetTagNames, Locale locale)
		throws PortalException, SystemException {

		boolean addDraftAssetEntry = false;

		if ((recordVersion != null) && !recordVersion.isApproved()) {
			String version = recordVersion.getVersion();

			if (!version.equals(DDLRecordConstants.DEFAULT_VERSION)) {
				int approvedRecordVersionsCount =
					ddlRecordVersionPersistence.countByR_S(
						record.getRecordId(),
						WorkflowConstants.STATUS_APPROVED);

				if (approvedRecordVersionsCount > 0) {
					addDraftAssetEntry = true;
				}
			}
		}

		boolean visible = false;

		if ((recordVersion != null) && !recordVersion.isApproved()) {
			visible = false;
		}

		DDLRecordSet recordSet = record.getRecordSet();

		String title = LanguageUtil.format(
			locale, "new-record-for-list-x", recordSet.getName(locale));

		if (addDraftAssetEntry) {
			assetEntryLocalService.updateEntry(
				userId, record.getGroupId(), DDLRecordConstants.getClassName(),
				recordVersion.getRecordVersionId(), record.getUuid(),
				assetCategoryIds, assetTagNames, false, null, null, null, null,
				ContentTypes.TEXT_HTML, title, null, StringPool.BLANK, null,
				null, 0, 0, null, false);
		}
		else {
			assetEntryLocalService.updateEntry(
				userId, record.getGroupId(), DDLRecordConstants.getClassName(),
				record.getRecordId(), record.getUuid(), assetCategoryIds,
				assetTagNames, visible, null, null, null, null,
				ContentTypes.TEXT_HTML, title, null, StringPool.BLANK, null,
				null, 0, 0, null, false);
		}
	}

	public DDLRecord updateRecord(
			long userId, long recordId, boolean majorVersion, int displayIndex,
			Fields fields, boolean mergeFields, ServiceContext serviceContext)
		throws PortalException, SystemException {

		// Record

		User user = userPersistence.findByPrimaryKey(userId);

		DDLRecord record = ddlRecordPersistence.findByPrimaryKey(recordId);

		record.setModifiedDate(serviceContext.getModifiedDate(null));

		ddlRecordPersistence.update(record, false);

		// Record version

		DDLRecordVersion recordVersion = record.getLatestRecordVersion();

		if (recordVersion.isApproved()) {
			DDLRecordSet recordSet = record.getRecordSet();

			long classPK = StorageEngineUtil.create(
				recordSet.getCompanyId(), recordSet.getDDMStructureId(), fields,
				serviceContext);
			String version = getNextVersion(
				record, majorVersion, serviceContext.getWorkflowAction());

			addRecordVersion(
				user, record, classPK, version, displayIndex,
				WorkflowConstants.STATUS_DRAFT);
		}
		else {
			StorageEngineUtil.update(
				recordVersion.getClassPK(), fields, mergeFields,
				serviceContext);

			String version = recordVersion.getVersion();

			updateRecordVersion(
				user, recordVersion, version, displayIndex,
				recordVersion.getStatus(), serviceContext);
		}

		// Workflow

		WorkflowHandlerRegistryUtil.startWorkflowInstance(
			user.getCompanyId(), record.getGroupId(), userId,
			DDLRecord.class.getName(), record.getRecordId(), record,
			serviceContext);

		return record;
	}

	public DDLRecord updateRecord(
			long userId, long recordId, int displayIndex,
			Map<String, Serializable> fieldsMap, boolean mergeFields,
			ServiceContext serviceContext)
		throws PortalException, SystemException {

		Fields fields = toFields(fieldsMap);

		return updateRecord(
			userId, recordId, false, displayIndex, fields, mergeFields,
			serviceContext);
	}

	public DDLRecord updateStatus(
			long userId, long recordId, int status,
			ServiceContext serviceContext)
		throws PortalException, SystemException {

		// Record

		User user = userPersistence.findByPrimaryKey(userId);

		DDLRecord record = ddlRecordPersistence.findByPrimaryKey(recordId);

		// Record version

		DDLRecordVersion latestRecordVersion = getLatestRecordVersion(
			record.getRecordId());

		latestRecordVersion.setStatus(status);
		latestRecordVersion.setStatusByUserId(user.getUserId());
		latestRecordVersion.setStatusByUserName(user.getFullName());
		latestRecordVersion.setStatusDate(new Date());

		ddlRecordVersionPersistence.update(latestRecordVersion, false);

		if (status == WorkflowConstants.STATUS_APPROVED) {
			if (DLUtil.compareVersions(
					record.getVersion(),
					latestRecordVersion.getVersion()) <= 0) {

				record.setClassNameId(latestRecordVersion.getClassNameId());
				record.setClassPK(latestRecordVersion.getClassPK());
				record.setVersion(latestRecordVersion.getVersion());
				record.setRecordSetId(latestRecordVersion.getRecordSetId());
				record.setDisplayIndex(latestRecordVersion.getDisplayIndex());
				record.setVersion(latestRecordVersion.getVersion());
				record.setVersionUserId(latestRecordVersion.getUserId());
				record.setVersionUserName(latestRecordVersion.getUserName());
				record.setModifiedDate(latestRecordVersion.getCreateDate());

				ddlRecordPersistence.update(record, false);
			}
		}
		else {
			if (record.getVersion().equals(
					latestRecordVersion.getVersion())) {

				String newVersion = DDLRecordConstants.DEFAULT_VERSION;

				List<DDLRecordVersion> approvedRecordVersions =
					ddlRecordVersionPersistence.findByR_S(
						record.getRecordId(),
						WorkflowConstants.STATUS_APPROVED);

				if (!approvedRecordVersions.isEmpty()) {
					newVersion = approvedRecordVersions.get(0).getVersion();
				}

				record.setVersion(newVersion);

				ddlRecordPersistence.update(record, false);
			}
		}

		return record;
	}

	protected DDLRecordVersion addRecordVersion(
			User user, DDLRecord record, long classPK, String version,
			int displayIndex, int status)
		throws SystemException {

		long recordVersionId = counterLocalService.increment();

		DDLRecordVersion recordVersion = ddlRecordVersionPersistence.create(
			recordVersionId);

		recordVersion.setGroupId(record.getGroupId());
		recordVersion.setCompanyId(record.getCompanyId());

		long versionUserId = record.getVersionUserId();

		if (versionUserId <= 0) {
			versionUserId = record.getUserId();
		}

		recordVersion.setUserId(versionUserId);

		String versionUserName = GetterUtil.getString(
			record.getVersionUserName(), record.getUserName());

		recordVersion.setUserName(versionUserName);

		recordVersion.setCreateDate(record.getModifiedDate());
		recordVersion.setClassNameId(record.getClassNameId());
		recordVersion.setClassPK(classPK);
		recordVersion.setRecordSetId(record.getRecordSetId());
		recordVersion.setRecordId(record.getRecordId());
		recordVersion.setVersion(version);
		recordVersion.setDisplayIndex(displayIndex);
		recordVersion.setStatus(status);
		recordVersion.setStatusByUserId(user.getUserId());
		recordVersion.setStatusByUserName(user.getFullName());
		recordVersion.setStatusDate(record.getModifiedDate());

		ddlRecordVersionPersistence.update(recordVersion, false);

		return recordVersion;
	}

	protected String getNextVersion(
		DDLRecord record, boolean majorVersion, int workflowAction) {

		if (workflowAction == WorkflowConstants.ACTION_SAVE_DRAFT) {
			majorVersion = false;
		}

		int[] versionParts = StringUtil.split(
			record.getVersion(), StringPool.PERIOD, 0);

		if (majorVersion) {
			versionParts[0]++;
			versionParts[1] = 0;
		}
		else {
			versionParts[1]++;
		}

		return versionParts[0] + StringPool.PERIOD + versionParts[1];
	}

	protected Fields toFields(Map<String, Serializable> fieldsMap) {
		Fields fields = new Fields();

		for (String name : fieldsMap.keySet()) {
			String value = String.valueOf(fieldsMap.get(name));

			Field field = new Field(name, value);

			fields.put(field);
		}

		return fields;
	}

	protected void updateRecordVersion(
			User user, DDLRecordVersion recordVersion, String version,
			int displayIndex, int status, ServiceContext serviceContext)
		throws SystemException {

		recordVersion.setVersion(version);
		recordVersion.setDisplayIndex(displayIndex);
		recordVersion.setStatus(status);
		recordVersion.setStatusByUserId(user.getUserId());
		recordVersion.setStatusByUserName(user.getFullName());
		recordVersion.setStatusDate(serviceContext.getModifiedDate(null));

		ddlRecordVersionPersistence.update(recordVersion, false);
	}

}