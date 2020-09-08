package org.mifosplatform.organisation.voucher.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import javax.ws.rs.core.StreamingOutput;

import org.joda.time.LocalDate;
import org.json.JSONObject;
import org.mifosplatform.crm.clientprospect.service.SearchSqlQuery;
import org.mifosplatform.infrastructure.configuration.domain.Configuration;
import org.mifosplatform.infrastructure.configuration.domain.ConfigurationConstants;
import org.mifosplatform.infrastructure.configuration.domain.ConfigurationRepository;
import org.mifosplatform.infrastructure.core.data.EnumOptionData;
import org.mifosplatform.infrastructure.core.domain.JdbcSupport;
import org.mifosplatform.infrastructure.core.exception.PlatformDataIntegrityException;
import org.mifosplatform.infrastructure.core.service.Page;
import org.mifosplatform.infrastructure.core.service.PaginationHelper;
import org.mifosplatform.infrastructure.core.service.TenantAwareRoutingDataSource;
import org.mifosplatform.infrastructure.dataqueries.data.GenericResultsetData;
import org.mifosplatform.infrastructure.dataqueries.data.ResultsetColumnHeaderData;
import org.mifosplatform.infrastructure.dataqueries.data.ResultsetRowData;
import org.mifosplatform.infrastructure.dataqueries.service.GenericDataService;
import org.mifosplatform.infrastructure.security.service.PlatformSecurityContext;
import org.mifosplatform.logistics.mrn.data.MRNDetailsData;
import org.mifosplatform.organisation.voucher.data.ExportVoucherData;
import org.mifosplatform.organisation.voucher.data.VoucherData;
import org.mifosplatform.organisation.voucher.data.VoucherPinConfigValueData;
import org.mifosplatform.organisation.voucher.data.VoucherRequestData;
import org.mifosplatform.organisation.voucher.domain.VoucherPinCategory;
import org.mifosplatform.organisation.voucher.domain.VoucherPinType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

/**
 * 
 * @author ashokreddy
 *
 */
@Service
public class VoucherReadPlatformServiceImpl implements VoucherReadPlatformService {

	private final JdbcTemplate jdbcTemplate;
	private final PlatformSecurityContext context;
	private final GenericDataService genericDataService;
	private final ConfigurationRepository configurationRepository;
	private final PaginationHelper<VoucherData> paginationHelper = new PaginationHelper<VoucherData>();

	@Autowired
	public VoucherReadPlatformServiceImpl(final PlatformSecurityContext context,
			final TenantAwareRoutingDataSource dataSource, final GenericDataService genericDataService,
			final ConfigurationRepository configurationRepository) {
		this.context = context;
		this.jdbcTemplate = new JdbcTemplate(dataSource);
		this.genericDataService = genericDataService;
		this.configurationRepository = configurationRepository;
	}

	@Override
	public String retrieveIndividualPin(String pinNo) {
		try {

			context.authenticatedUser();
			String sql;
			retrieveMapper mapper = new retrieveMapper();
			sql = "SELECT  " + mapper.schema();

			return this.jdbcTemplate.queryForObject(sql, mapper, new Object[] { pinNo });
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	private static final class retrieveMapper implements RowMapper<String> {

		private String schema() {
			return " d.pin_no as pinNo from b_pin_details d where d.pin_no =?";

		}

		@Override
		public String mapRow(final ResultSet rs, final int rowNum) throws SQLException {
			return rs.getString("pinNo");

		}
	}

	@Override
	public List<EnumOptionData> pinCategory() {

		EnumOptionData numeric = VoucherEnumeration.enumOptionData(VoucherPinCategory.NUMERIC);
		EnumOptionData alpha = VoucherEnumeration.enumOptionData(VoucherPinCategory.ALPHA);
		EnumOptionData alphaNumeric = VoucherEnumeration.enumOptionData(VoucherPinCategory.ALPHANUMERIC);
		List<EnumOptionData> categotyType = Arrays.asList(numeric, alpha, alphaNumeric);
		return categotyType;
	}

	@Override
	public List<EnumOptionData> pinType() {

		EnumOptionData value = VoucherEnumerationType.enumOptionData(VoucherPinType.VALUE);
		EnumOptionData duration = VoucherEnumerationType.enumOptionData(VoucherPinType.DURATION);
		List<EnumOptionData> categotyType = Arrays.asList(value, duration);
		return categotyType;
	}

	@Override
	public VoucherPinConfigValueData getVoucherPinConfigValues(String configVoucherpinValues) {
		// TODO Auto-generated method stub
		VoucherPinConfigValueData voucherPinConfigValueData = new VoucherPinConfigValueData();
		Configuration isVoucherPinConfigValues = configurationRepository
				.findOneByName(ConfigurationConstants.CONFIG_VOUCHERPIN_VALUES);
		try {
			if (null != isVoucherPinConfigValues && isVoucherPinConfigValues.isEnabled()) {
				final JSONObject object = new JSONObject(isVoucherPinConfigValues.getValue());
				voucherPinConfigValueData.setPinLength(object.getString("length_pin"));
				voucherPinConfigValueData.setPinCategory(object.getString("pin_category"));
				voucherPinConfigValueData.setBeginWith(object.getString("begin_with"));
				voucherPinConfigValueData.setLenghtSerial(object.getString("length_serial"));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return voucherPinConfigValueData;
	}

	@Override
	public Page<VoucherData> getAllVoucherByOfficeId(Long pinId) {
		try {

			context.authenticatedUser();
			retrieveRandomMapper mapper = new retrieveRandomMapper();
			StringBuilder sqlBuilder = new StringBuilder();

			sqlBuilder.append("SELECT ");
			sqlBuilder.append(mapper.schema());
			sqlBuilder.append(" where pd.is_deleted='N' and pm.id=?");
			return this.paginationHelper.fetchPage(this.jdbcTemplate, "SELECT FOUND_ROWS()", sqlBuilder.toString(),
					new Object[] { pinId }, mapper);
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	@Override
	public Page<VoucherData> getAllVoucherById(SearchSqlQuery searchVoucher, String statusType, Long id) {
		try {

			context.authenticatedUser();
			retrieveRandomMapper mapper = new retrieveRandomMapper();
			StringBuilder sqlBuilder = new StringBuilder();

			sqlBuilder.append("SELECT ");
			sqlBuilder.append(mapper.schema());
			sqlBuilder.append(" where pd.is_deleted='N' and pd.pin_id=?");
			String sqlSearch = searchVoucher.getSqlSearch();
			String extraCriteria = "";
			if (statusType != null) {
				sqlBuilder.append(" and (pd.status ='" + statusType + "') ");
			}
			if (sqlSearch != null) {
				sqlSearch = sqlSearch.trim();
				extraCriteria = " and (pd.pin_no like '%" + sqlSearch + "%' OR" + " pd.client_id like '%" + sqlSearch
						+ "%' OR" + " pd.serial_no like '%" + sqlSearch + "%' )";
			}

			sqlBuilder.append(extraCriteria);

			if (searchVoucher.isLimited()) {
				sqlBuilder.append(" limit ").append(searchVoucher.getLimit());
			}

			if (searchVoucher.isOffset()) {
				sqlBuilder.append(" offset ").append(searchVoucher.getOffset());
			}

			return this.paginationHelper.fetchPage(this.jdbcTemplate, "SELECT FOUND_ROWS()", sqlBuilder.toString(),
					new Object[] { id }, mapper);
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	@Override
	public List<VoucherData> getAllVoucherByStatus(String statusType, Long quantity) {
		try {

			context.authenticatedUser();
			retrieveRandomMapper mapper = new retrieveRandomMapper();

			String sql = "SELECT " + mapper.schema() + " where pd.is_deleted='N' and pd.status ='" + statusType
					+ "' limit " + quantity + "";

			return this.jdbcTemplate.query(sql, mapper, new Object[] {});
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	private static final class retrieveRandomMapper implements RowMapper<VoucherData> {

		public String schema() {
			/*
			 * return
			 * "m.id as id, m.batch_name as batchName, m.office_id as officeId, m.length as length,"
			 * +
			 * "m.begin_with as beginWith,m.pin_category as pinCategory,m.quantity as quantity,"
			 * +
			 * "m.serial_no as serialNo,m.pin_type as pinType,m.pin_value as pinValue,m.expiry_date as expiryDate, "
			 * +
			 * "case m.pin_type when 'VALUE' then p.plan_code=null when 'PRODUCT' then p.plan_code end as planCode, "
			 * + "m.is_processed as isProcessed from b_pin_master m  " +
			 * "left join b_plan_master p on m.pin_value=p.id";
			 */
			return " pd.id, pm.batch_name AS batchName, pm.pin_type AS pinType, pm.office_id as officeId,"
					+ "pm.pin_value AS pinValue, pm.pin_category as pinCategory, pd.serial_no as serialNo,"
					+ "pd.pin_no as pinNo, pd.status as status, pd.client_id as clientId, pm.expiry_date as expiryDate "
					+ "FROM b_pin_master pm left join b_pin_details pd on pd.pin_id = pm.id  ";

		}

		@Override
		public VoucherData mapRow(final ResultSet rs, final int rowNum) throws SQLException {

			Long id = rs.getLong("id");
			String batchName = rs.getString("batchName");
			Long officeId = rs.getLong("officeId");
			// Long length = rs.getLong("length");
			String pinCategory = rs.getString("pinCategory");
			String pinType = rs.getString("pinType");
			// Long quantity = rs.getLong("quantity");
			String serial = rs.getString("serialNo");
			Date expiryDate = rs.getDate("expiryDate");
			// String beginWith = rs.getString("beginWith");
			String pinValue = rs.getString("pinValue");
			// String planCode = rs.getString("planCode");
			// String isProcessed = rs.getString("isProcessed");
			String pinNo = rs.getString("pinNo");
			String status = rs.getString("status");
			Long clientId = rs.getLong("clientId");

			return new VoucherData(batchName, officeId, null, pinCategory, pinType, null, serial, expiryDate, null,
					pinValue, id, null, null, null, null, pinNo, status, clientId);

		}
	}

	@Override
	public Long retrieveMaxNo(Long minNo, Long maxNo) {
		try {
			context.authenticatedUser();
			String sql;
			Mapper mapper = new Mapper();
			sql = "SELECT  " + mapper.schema();

			return this.jdbcTemplate.queryForObject(sql, mapper, new Object[] { minNo, maxNo });
		} catch (EmptyResultDataAccessException e) {
			return Long.valueOf(0);
		}
	}

	private static final class Mapper implements RowMapper<Long> {

		public String schema() {
			return "max(m.serial_no) as serialNo from b_pin_details m where serial_no BETWEEN ? AND ?";

		}

		@Override
		public Long mapRow(final ResultSet rs, final int rowNum) throws SQLException {
			Long serialNo = rs.getLong("serialNo");

			return serialNo;
		}
	}

	@Override
	public StreamingOutput retrieveVocherDetailsCsv(final Long batchId) {
		this.context.authenticatedUser();
		return new StreamingOutput() {

			@Override
			public void write(final OutputStream out) {
				try {
					String sql = null;
					Configuration isPaywizard = configurationRepository
							.findOneByName(ConfigurationConstants.PAYWIZARD_INTEGRATION);

					if (null != isPaywizard && isPaywizard.isEnabled()) {

					  sql = "SELECT pm.batch_name AS batchName, pd.serial_no AS serialNum,"
							+ " pd.pin_no AS hiddenNum, pd.client_id as clientId, pd.status, "
							+ "pm.pin_value as pinValue,pm.created_date as startDate,pm.expiry_date as endDate,"
							+ "'NGN' as currencyCode FROM b_pin_master pm, b_pin_details pd"

							+ " WHERE pd.pin_id = pm.id AND pm.id ="
							+ batchId
							+ " order by serialNum desc ";
					}else {
						sql = "SELECT pm.id AS batchId, pd.serial_no AS serialNum, pd.pin_no AS hiddenNum,"
								+ " pd.client_id as clientId, pd.status FROM b_pin_master pm, b_pin_details pd"
									+ " WHERE pd.pin_id = pm.id AND pm.id ="
									+ batchId
									+ " order by serialNum desc ";
						
					}
					GenericResultsetData result = genericDataService
							.fillGenericResultSet(sql);

					StringBuffer sb = generateCsvFileBuffer(result);
					InputStream in = new ByteArrayInputStream(sb.toString().getBytes("UTF-8"));
					byte[] outputByte = new byte[4096];
					Integer readLen = in.read(outputByte, 0, 4096);
					while (readLen != -1) {
						out.write(outputByte, 0, readLen);
						readLen = in.read(outputByte, 0, 4096);
					}
				} catch (Exception e) {

					throw new PlatformDataIntegrityException("error.msg.exception.error", e.getMessage());
				}
			}
		};
	}

	private StringBuffer generateCsvFileBuffer(final GenericResultsetData result) {
		StringBuffer writer = new StringBuffer();

		List<ResultsetColumnHeaderData> columnHeaders = result.getColumnHeaders();
		// logger.info("NO. of Columns: " + columnHeaders.size());
		Integer chSize = columnHeaders.size();
		for (int i = 0; i < chSize; i++) {
			writer.append('"' + columnHeaders.get(i).getColumnName() + '"');
			if (i < (chSize - 1))
				writer.append(",");
		}
		writer.append('\n');

		List<ResultsetRowData> data = result.getData();
		List<String> row;
		Integer rSize;
		// String currCol;
		String currColType;
		String currVal;
		String doubleQuote = "\"";
		String twoDoubleQuotes = doubleQuote + doubleQuote;
		// logger.info("NO. of Rows: " + data.size());
		for (int i = 0; i < data.size(); i++) {
			row = data.get(i).getRow();
			rSize = row.size();
			for (int j = 0; j < rSize; j++) {
				// currCol = columnHeaders.get(j).getColumnName();
				currColType = columnHeaders.get(j).getColumnType();
				currVal = row.get(j);
				if (currVal != null) {
					if (currColType.equals("DECIMAL") || currColType.equals("DOUBLE") || currColType.equals("BIGINT")
							|| currColType.equals("SMALLINT") || currColType.equals("INT"))
						writer.append(currVal);
					else
						writer.append('"' + genericDataService.replace(currVal, doubleQuote, twoDoubleQuotes) + '"');

				}
				if (j < (rSize - 1))
					writer.append(",");
			}
			writer.append('\n');
		}

		return writer;
	}

	@Override
	public StreamingOutput retrieveVocherDetailsCsvByStatus(final String status) {
		this.context.authenticatedUser();
		return new StreamingOutput() {

			@Override
			public void write(final OutputStream out) {
				try {

					final String sql = "SELECT pm.id AS batchId, pd.serial_no AS serialNum,pd.pin_no as pinNo,"
							+ " pd.status as status,pd.office_id as officeId,pd.sale_ref_no as saleRefNumber,pm.quantity as quantity "
							+ "FROM b_pin_master pm, b_pin_details pd"
							+ " WHERE pd.pin_id = pm.id AND pd.status='" + status + "'" + " order by serialNum desc ";
					GenericResultsetData result = genericDataService.fillGenericResultSet(sql);
					StringBuffer sb = generateCsvFileBufferByStatus(result);
					InputStream in = new ByteArrayInputStream(sb.toString().getBytes("UTF-8"));
					byte[] outputByte = new byte[4096];
					Integer readLen = in.read(outputByte, 0, 4096);
					while (readLen != -1) {
						out.write(outputByte, 0, readLen);
						readLen = in.read(outputByte, 0, 4096);
					}
				} catch (Exception e) {

					throw new PlatformDataIntegrityException("error.msg.exception.error", e.getMessage());
				}
			}
		};
	}

	private StringBuffer generateCsvFileBufferByStatus(final GenericResultsetData result) {
		StringBuffer writer = new StringBuffer();

		List<ResultsetColumnHeaderData> columnHeaders = result.getColumnHeaders();
		// logger.info("NO. of Columns: " + columnHeaders.size());
		Integer chSize = columnHeaders.size();
		for (int i = 0; i < chSize; i++) {
			writer.append('"' + columnHeaders.get(i).getColumnName() + '"');
			if (i < (chSize - 1))
				writer.append(",");
		}
		writer.append('\n');

		List<ResultsetRowData> data = result.getData();
		List<String> row;
		Integer rSize;
		// String currCol;
		String currColType;
		String currVal;
		String doubleQuote = "\"";
		String twoDoubleQuotes = doubleQuote + doubleQuote;
		// logger.info("NO. of Rows: " + data.size());
		for (int i = 0; i < data.size(); i++) {
			row = data.get(i).getRow();
			rSize = row.size();
			for (int j = 0; j < rSize; j++) {
				// currCol = columnHeaders.get(j).getColumnName();
				currColType = columnHeaders.get(j).getColumnType();
				currVal = row.get(j);
				if (currVal != null) {
					if (currColType.equals("DECIMAL") || currColType.equals("DOUBLE") || currColType.equals("BIGINT")
							|| currColType.equals("SMALLINT") || currColType.equals("INT"))
						writer.append(currVal);
					else
						writer.append('"' + genericDataService.replace(currVal, doubleQuote, twoDoubleQuotes) + '"');

				}
				if (j < (rSize - 1))
					writer.append(",");
			}
			writer.append('\n');
		}

		return writer;
	}

	@Override
	public List<VoucherData> retrivePinDetails(String pinNumber) {

		try {
			context.authenticatedUser();
			String sql;
			PinMapper mapper = new PinMapper();
			sql = "SELECT  " + mapper.schema();

			return this.jdbcTemplate.query(sql, mapper, new Object[] { pinNumber });

		} catch (EmptyResultDataAccessException e) {
			return null;
		}

	}

	private static final class PinMapper implements RowMapper<VoucherData> {

		public String schema() {
			return " pm.pin_type as pinType,pd.office_id as officeId, pm.pin_value as pinValue, pm.expiry_date as expiryDate "
					+ " from b_pin_master pm, b_pin_details pd where pd.pin_id = pm.id and pd.status !='USED' and pd.is_deleted='N' and pd.pin_no=? ";

		}

		@Override
		public VoucherData mapRow(final ResultSet rs, final int rowNum) throws SQLException {
			String pinValue = rs.getString("pinValue");
			Date expiryDate = rs.getDate("expiryDate");
			String pinType = rs.getString("pinType");
			Long officeId = rs.getLong("officeId");

			return new VoucherData(pinType, pinValue, expiryDate, officeId);
		}
	}

	@Override
	public VoucherData retriveVoucherPinDetails(String pinNumber, Long officeId) {

		try {
			context.authenticatedUser();
			String sql;
			VoucherMapper mapper = new VoucherMapper();
			sql = "SELECT  " + mapper.schema();
			Configuration restrictToHierarchy = configurationRepository
					.findOneByName(ConfigurationConstants.Restrict_To_Hierarchy);

			if (restrictToHierarchy.isEnabled()) {
				return this.jdbcTemplate.queryForObject(sql, mapper, new Object[] { pinNumber, officeId });
			} else {
				return this.jdbcTemplate.queryForObject(sql, mapper, new Object[] { pinNumber });
			}
		} catch (EmptyResultDataAccessException e) {
			return null;
		}

	}

	private final class VoucherMapper implements RowMapper<VoucherData> {

		public String schema() {
			Configuration restrictToHierarchy = configurationRepository
					.findOneByName(ConfigurationConstants.Restrict_To_Hierarchy);
			String schema = null;

			if (restrictToHierarchy.isEnabled()) {
				schema = " pm.pin_type as pinType,pd.office_id as officeId, pm.pin_value as pinValue, pm.expiry_date as expiryDate "
						+ " from b_pin_master pm, b_pin_details pd where pd.pin_id = pm.id and pd.status !='USED' and pd.is_deleted='N' and pd.pin_no=? and pd.office_id = ?";
			} else {
				schema = " pm.pin_type as pinType,pd.office_id as officeId, pm.pin_value as pinValue, pm.expiry_date as expiryDate "
						+ " from b_pin_master pm, b_pin_details pd where pd.pin_id = pm.id and pd.status !='USED' and pd.is_deleted='N' and pd.pin_no=?";

			}
			return schema;

		}

		@Override
		public VoucherData mapRow(final ResultSet rs, final int rowNum) throws SQLException {
			String pinValue = rs.getString("pinValue");
			Date expiryDate = rs.getDate("expiryDate");
			String pinType = rs.getString("pinType");
			Long officeId = rs.getLong("officeId");

			return new VoucherData(pinType, pinValue, expiryDate, officeId);
		}
	}

	@Override
	public VoucherData retriveVoucherPinDetailsWithPriceValue(String pinNumber, Long officeId, BigDecimal eventValue) {

		try {
			context.authenticatedUser();
			String sql = null;
			VoucherValueMapper mapper = new VoucherValueMapper();
			
			sql = "SELECT  " + mapper.schema();

			return this.jdbcTemplate.queryForObject(sql, mapper, new Object[] { eventValue, pinNumber, officeId });

		} catch (EmptyResultDataAccessException e) {
			return null;
		}

	}

	private static final class VoucherValueMapper implements RowMapper<VoucherData> {

		public String schema() {
			
			
			return " pm.pin_type as pinType,pd.office_id as officeId, pm.pin_value as pinValue, pm.expiry_date as expiryDate "
					+ " from b_pin_master pm, b_pin_details pd where pd.pin_id = pm.id and pd.status !='USED' and pd.is_deleted='N' and pm.pin_value >= ?   "
					+ " and pd.pin_no=? and pd.office_id = ?";
			}

		

		@Override
		public VoucherData mapRow(final ResultSet rs, final int rowNum) throws SQLException {
			String pinValue = rs.getString("pinValue");
			Date expiryDate = rs.getDate("expiryDate");
			String pinType = rs.getString("pinType");
			Long officeId = rs.getLong("officeId");

			return new VoucherData(pinType, pinValue, expiryDate, officeId);
		}
	}

	@Override
	public List<VoucherData> getAllData() {
		try {

			context.authenticatedUser();
			String sql;
			RetrieveAllRandomMapper mapper = new RetrieveAllRandomMapper();
			sql = "SELECT  " + mapper.schema();

			return this.jdbcTemplate.query(sql, mapper, new Object[] {});
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	private static final class RetrieveAllRandomMapper implements RowMapper<VoucherData> {

		public String schema() {

			return "m.id as id, m.batch_name as batchName, m.office_id as officeId, m.length as length,"
					+ "m.begin_with as beginWith,m.pin_category as pinCategory,m.quantity as quantity,"
					+ "m.serial_no as serialNo,m.pin_type as pinType,m.pin_value as pinValue,m.expiry_date as expiryDate,m.batch_type as batchType, "
					+ "case m.pin_type when 'VALUE' then p.plan_code=null when 'PRODUCT' then p.plan_code end as planCode,case m.pin_type when 'COUPON' then pr.promotion_description end as promotionDescription, "
					+ "m.is_processed as isProcessed from b_pin_master m  "
					+ "left join b_plan_master p on m.pin_value=p.id "
					+ "left join b_promotion_master pr ON m.pin_value = pr.id";

		}

		@Override
		public VoucherData mapRow(final ResultSet rs, final int rowNum) throws SQLException {

			Long id = rs.getLong("id");
			String batchName = rs.getString("batchName");
			Long officeId = rs.getLong("officeId");
			Long length = rs.getLong("length");
			String pinCategory = rs.getString("pinCategory");
			String pinType = rs.getString("pinType");
			Long quantity = rs.getLong("quantity");
			String serial = rs.getString("serialNo");
			Date expiryDate = rs.getDate("expiryDate");
			String beginWith = rs.getString("beginWith");
			String pinValue = rs.getString("pinValue");
			String planCode = rs.getString("planCode");
			String batchType = rs.getString("batchType");
			String promotionDescription = rs.getString("promotionDescription");
			String isProcessed = rs.getString("isProcessed");

			return new VoucherData(batchName, officeId, length, pinCategory, pinType, quantity, serial, expiryDate,
					beginWith, pinValue, id, planCode, batchType, promotionDescription, isProcessed, null, null, null);

		}
	}

	/*
	 * @Override public List<VoucherData> getVocherDetailsByPurchaseNo(String
	 * purchaseNo) {
	 * 
	 * try {
	 * 
	 * context.authenticatedUser(); String sql; VoucherByBatchNameMapper mapper =
	 * new VoucherByBatchNameMapper(); sql = "SELECT " + mapper.schema()+
	 * "like '%"+purchaseNo+"%'";
	 * 
	 * return this.jdbcTemplate.query(sql, mapper, new Object[] {}); } catch
	 * (EmptyResultDataAccessException e) { return null; }
	 * 
	 * } private class VoucherByBatchNameMapper implements RowMapper<VoucherData>{
	 * 
	 * public String schema() {
	 * 
	 * return
	 * " p.id as voucherId,p.batch_name as batchName, p.length as length, p.pin_category as pinCategory,"
	 * +
	 * "p.quantity as quantity, p.begin_with as beginWith, p.serial_no as serialNo, p.expiry_date as expiryDate,"
	 * +
	 * "p.pin_value as amount, p.is_processed as isProcessed ,p.pin_type as pinType, p.office_id as officeId, "
	 * + "p.batch_type as batchType from b_pin_master p where batch_name ";
	 * 
	 * }
	 * 
	 * @Override public VoucherData mapRow(ResultSet rs, int rowNum)throws
	 * SQLException {
	 * 
	 * Long voucherId = rs.getLong("voucherId"); String batchName =
	 * rs.getString("batchName"); Long officeId = rs.getLong("officeId"); Long
	 * length = rs.getLong("length"); String pinCategory =
	 * rs.getString("pinCategory"); String pinType = rs.getString("pinType"); Long
	 * quantity = rs.getLong("quantity"); String serial = rs.getString("serialNo");
	 * Date expiryDate = rs.getDate("expiryDate"); String beginWith =
	 * rs.getString("beginWith"); String amount = rs.getString("amount"); String
	 * batchType = rs.getString("batchType"); String isProcessed =
	 * rs.getString("isProcessed");
	 * 
	 * return new VoucherData(batchName, officeId, length, pinCategory, pinType,
	 * quantity, serial, expiryDate, beginWith, amount, voucherId, null,
	 * batchType,null,isProcessed, null, null, null); } }
	 */

	@Override
	public StreamingOutput retrieveVocherDetailsCsvByBatchName(String batchName) {
		// TODO Auto-generated method stub
		this.context.authenticatedUser();
		return new StreamingOutput() {

			@Override
			public void write(final OutputStream out) {
				try {

					final String sql = "SELECT pm.batch_name AS batchName, pd.serial_no AS serialNum,"
							+ " pd.pin_no AS hiddenNum, pd.client_id as clientId, pd.status FROM b_pin_master pm, b_pin_details pd"
							+ " WHERE pd.pin_id = pm.id AND pm.batch_name ='" + batchName + "'"
							+ " order by serialNum desc ";
					GenericResultsetData result = genericDataService.fillGenericResultSet(sql);
					StringBuffer sb = generateCsvFileBufferForBatchName(result);
					InputStream in = new ByteArrayInputStream(sb.toString().getBytes("UTF-8"));
					byte[] outputByte = new byte[4096];
					Integer readLen = in.read(outputByte, 0, 4096);
					while (readLen != -1) {
						out.write(outputByte, 0, readLen);
						readLen = in.read(outputByte, 0, 4096);
					}
				} catch (Exception e) {

					throw new PlatformDataIntegrityException("error.msg.exception.error", e.getMessage());
				}
			}
		};

	}

	private StringBuffer generateCsvFileBufferForBatchName(final GenericResultsetData result) {
		StringBuffer writer = new StringBuffer();

		List<ResultsetColumnHeaderData> columnHeaders = result.getColumnHeaders();
		// logger.info("NO. of Columns: " + columnHeaders.size());
		Integer chSize = columnHeaders.size();
		for (int i = 0; i < chSize; i++) {
			writer.append('"' + columnHeaders.get(i).getColumnName() + '"');
			if (i < (chSize - 1))
				writer.append(",");
		}
		writer.append('\n');

		List<ResultsetRowData> data = result.getData();
		List<String> row;
		Integer rSize;
		// String currCol;
		String currColType;
		String currVal;
		String doubleQuote = "\"";
		String twoDoubleQuotes = doubleQuote + doubleQuote;
		// logger.info("NO. of Rows: " + data.size());
		for (int i = 0; i < data.size(); i++) {
			row = data.get(i).getRow();
			rSize = row.size();
			for (int j = 0; j < rSize; j++) {
				// currCol = columnHeaders.get(j).getColumnName();
				currColType = columnHeaders.get(j).getColumnType();
				currVal = row.get(j);
				if (currVal != null) {
					if (currColType.equals("DECIMAL") || currColType.equals("DOUBLE") || currColType.equals("BIGINT")
							|| currColType.equals("SMALLINT") || currColType.equals("INT"))
						writer.append(currVal);
					else
						writer.append('"' + genericDataService.replace(currVal, doubleQuote, twoDoubleQuotes) + '"');

				}
				if (j < (rSize - 1))
					writer.append(",");
			}
			writer.append('\n');
		}

		return writer;
	}

	@Override
	public List<VoucherData> retrieveVocherDetails(Long saleRefId) {
		// TODO Auto-generated method stub
		this.context.authenticatedUser();
		String sql;
		try {
			RetrieveVoucherMapper mapper = new RetrieveVoucherMapper();
			sql = "SELECT  " + mapper.schema() + " WHERE pd.sale_ref_no = " + saleRefId + " order by serialNo desc";

			return this.jdbcTemplate.query(sql, mapper, new Object[] {});
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	@Override
	public List<VoucherData> retrieveVocherDetailsBySaleRefId(Long saleRefId, Long quantity) {
		// TODO Auto-generated method stub
		this.context.authenticatedUser();
		String sql;
		try {
			RetrieveVoucherMapper mapper = new RetrieveVoucherMapper();
			sql = "SELECT  " + mapper.schema() + " WHERE pd.sale_ref_no = " + saleRefId
					+ " and pd.status = 'ALLOCATED' order by serialNo desc limit " + quantity + "";

			return this.jdbcTemplate.query(sql, mapper, new Object[] {});
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	private static final class RetrieveVoucherMapper implements RowMapper<VoucherData> {

		public String schema() {

			return "pd.sale_ref_no as saleRefNo ,pd.status as status ,pd.serial_no AS serialNo, pd.pin_no AS pinNum, pd.office_id as officeId, pd.status FROM  b_pin_details pd  ";

		}

		@Override
		public VoucherData mapRow(final ResultSet rs, final int rowNum) throws SQLException {

			Long saleRefNo = rs.getLong("saleRefNo");
			String serial = rs.getString("serialNo");
			String pinNum = rs.getString("pinNum");
			// String status = rs.getString("status");
			Long officeId = rs.getLong("officeId");
			String status = rs.getString("status");
			return new VoucherData(saleRefNo, serial, pinNum, status, officeId);

		}
	}

	@SuppressWarnings("deprecation")
	@Override
	public Long retriveQuantityByStatus(String status, Long fromOffice, BigDecimal unitPrice, Boolean isProduct) {

		// TODO Auto-generated method stub
		this.context.authenticatedUser();
		try {
			String sql = null;
			if (isProduct) {
				sql = "select count(*) from b_pin_master pm left join  b_plan_pricing pp "
						+ "on pm.price_id=pp.id join  b_pin_details pd on pm.id=pd.pin_id "
						+ " WHERE pm.pin_type= 'PRODUCT' and pd.status='" + status + "' " + "and pd.office_id= "
						+ fromOffice + " and pp.price=" + unitPrice + " ";
			} else {
				sql = "select count(0) as count from b_pin_details pd join b_pin_master pm on pm.id=pd.pin_id where pd.status= '"
						+ status + "' and pd.office_id=" + fromOffice + " and pm.pin_value=" + unitPrice + " ";
			}
			return this.jdbcTemplate.queryForLong(sql);

		} catch (EmptyResultDataAccessException e) {
			return null;
		}

	}

	@SuppressWarnings("deprecation")
	@Override
	public Long retriveQuantityBySaleRefId(Long saleRefId) {
		// TODO Auto-generated method stub
		this.context.authenticatedUser();
		try {

			String sql = "select count(0) as count from b_pin_details where status= 'ALLOCATED' and sale_ref_no = "
					+ saleRefId + " ";
			return this.jdbcTemplate.queryForLong(sql);
		} catch (EmptyResultDataAccessException e) {
			return null;
		}

	}

	/*
	 * private static final class StatusMapper implements RowMapper<Long> {
	 * 
	 * @Override public Long mapRow(final ResultSet rs, final int rowNum) throws
	 * SQLException { Long count = rs.getLong("count");
	 * 
	 * return count ; } }
	 */
	@Override
	public VoucherRequestData retrieveVocherRequestDetails(Long saleRefId) {
		// TODO Auto-generated method stub
		this.context.authenticatedUser();
		VoucherRequestData requestData;

		try {

			List<VoucherData> vouchers = retrieveVocherDetails(saleRefId);

			VoucheRequestDetailsMapper reqMapper = new VoucheRequestDetailsMapper();
			final String sql = "select " + reqMapper.schema()
					+ " from b_itemsale its join b_pin_details pd on its.id= pd.sale_ref_no " + " where its.id = "
					+ saleRefId + "  group by its.id ";
			// List<VoucherData> vouchers = (List<VoucherData>) this.jdbcTemplate.query(sql,
			// mapper, new Object[] {});

			requestData = this.jdbcTemplate.queryForObject(sql, reqMapper, new Object[] {});
			requestData.setVoucherData(vouchers);
			return requestData;
		} catch (EmptyResultDataAccessException e) {
			return null;
		}

	}

	private static final class VoucheRequestDetailsMapper implements RowMapper<VoucherRequestData> {

		public String schema() {
			return " its.id as id," + "its.purchase_date as requestedDate, "
					+ "(select item_description from b_item_master where id=its.item_id) as item,"
					+ "its.order_quantity as orderdQuantity, "
					+ "its.received_quantity as receivedQuantity,(select count(1) from  b_pin_details where sale_ref_no = its.id and status = 'EXPORTED') as exportedQuantity,"
					+ "(select count(1) from  b_pin_details where sale_ref_no = its.id and status = 'USED') as reedeemedQuantity,"
					+ "(select count(1) from  b_pin_details where sale_ref_no = its.id and status = 'ALLOCATED') as allocatedQuantity,"
					+ "its.status as status, its.charge_amount as chargeAmount, its.unit_price as unitPrice ";

		}

		@Override
		public VoucherRequestData mapRow(final ResultSet rs, final int rowNum) throws SQLException {
			final Long id = rs.getLong("id");
			final Date requestedDate = JdbcSupport.getLocalDate(rs, "requestedDate").toDate();
			final String itemDescription = rs.getString("item");
			final Long orderdQuantity = rs.getLong("orderdQuantity");
			final Long receivedQuantity = rs.getLong("receivedQuantity");
			final Long exportedQuantity = rs.getLong("exportedQuantity");
			final Long redeemedQuantity = rs.getLong("reedeemedQuantity");
			final Long allocatedQuantity = rs.getLong("allocatedQuantity");
			final String status = rs.getString("status");
			final BigDecimal chargeAmount = rs.getBigDecimal("chargeAmount");
			final BigDecimal unitPrice = rs.getBigDecimal("unitPrice");

			/* final String notes = rs.getString("notes"); */

			return new VoucherRequestData(id, requestedDate, orderdQuantity, exportedQuantity, redeemedQuantity,
					receivedQuantity, status, itemDescription, chargeAmount, unitPrice, allocatedQuantity);

		}
	}

	@Override
	public List<ExportVoucherData> retrieveExportRequestDetails(Long officeId) {
		// TODO Auto-generated method stub
		try {
			ExportRequestMapper exportMapper = new ExportRequestMapper();
			String sql = "select " + exportMapper.schema() + " from b_voucher_export_request where request_by = "
					+ officeId;
			return this.jdbcTemplate.query(sql, exportMapper, new Object[] {});
		} catch (EmptyResultDataAccessException e) {
			return null;
		}

	}

	private static final class ExportRequestMapper implements RowMapper<ExportVoucherData> {

		public String schema() {
			return "  request_id as requestId , request_date as requestedDate, status as status, quantity as quantity, request_by as officeId, sale_ref_no as saleRefNo ";
		}

		@Override
		public ExportVoucherData mapRow(final ResultSet rs, final int rowNum) throws SQLException {
			final String id = rs.getString("requestId");
			final Date requestedDate = JdbcSupport.getLocalDate(rs, "requestedDate").toDate();
			final Long saleRefNo = rs.getLong("saleRefNo");
			final Long exportedQuantity = rs.getLong("quantity");
			final String status = rs.getString("status");
			final Long officeId = rs.getLong("officeId");
			return new ExportVoucherData(id, requestedDate, status, saleRefNo, exportedQuantity, officeId);

		}
	}

	@Override
	public ExportVoucherData retrieveExportRequestDetailsByRequestId(String requestId) {
		// TODO Auto-generated method stub
		try {
			ExportRequestMapper exportMapper = new ExportRequestMapper();
			String sql = "select " + exportMapper.schema() + " from b_voucher_export_request where request_id = "
					+ requestId;
			return this.jdbcTemplate.queryForObject(sql, exportMapper, new Object[] {});
		} catch (EmptyResultDataAccessException e) {
			return null;
		}

	}

	@Override
	public List<VoucherData> retriveVoucherDetailsByRequestId(String requestId) {

		try {
			context.authenticatedUser();
			String sql;
			RequestByRequestId mapper = new RequestByRequestId();
			sql = "SELECT  " + mapper.schema();

			return this.jdbcTemplate.query(sql, mapper, new Object[] { requestId });

		} catch (EmptyResultDataAccessException e) {
			return null;
		}

	}

	private static final class RequestByRequestId implements RowMapper<VoucherData> {

		public String schema() {
			return " pm.pin_type as pinType,pd.status as status,pd.pin_no as pinNum,pd.serial_no as serialNo, pm.pin_value as pinValue, pm.expiry_date as expiryDate "
					+ " from b_pin_master pm, b_pin_details pd where pd.pin_id = pm.id and pd.is_deleted='N' and pd.export_req_id=?";

		}

		@Override
		public VoucherData mapRow(final ResultSet rs, final int rowNum) throws SQLException {
			String pinValue = rs.getString("pinValue");
			Date expiryDate = rs.getDate("expiryDate");
			String pinType = rs.getString("pinType");
			String serial = rs.getString("serialNo");
			String pinNum = rs.getString("pinNum");
			String status = rs.getString("status");
			return new VoucherData(pinType, pinValue, expiryDate, serial, pinNum, status);
		}
	}

	@Override
	public ExportVoucherData exportVoucherDetails(String requestId) {
		// TODO Auto-generated method stub
		ExportVoucherData exportVoucherData;
		try {
			context.authenticatedUser();
			String sql;
			ExportRequestMapper exportMapper = new ExportRequestMapper();
			List<VoucherData> voucherData = this.retriveVoucherDetailsByRequestId(requestId);
			sql = "select " + exportMapper.schema() + " from b_voucher_export_request where request_id = ?";
			exportVoucherData = this.jdbcTemplate.queryForObject(sql, exportMapper, new Object[] { requestId });
			exportVoucherData.setVoucherData(voucherData);
			return exportVoucherData;
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	@Override
	public VoucherData retriveVoucherPinDetailsWithOutOffice(String voucherId) {
		// TODO Auto-generated method stub

		try {
			context.authenticatedUser();
			String sql;
			PinMapper mapper = new PinMapper();
			sql = "SELECT  " + mapper.schema();

			return this.jdbcTemplate.queryForObject(sql, mapper, new Object[] { voucherId });

		} catch (EmptyResultDataAccessException e) {
			return null;
		}

	}

}
