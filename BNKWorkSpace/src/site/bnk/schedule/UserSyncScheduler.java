package site.bnk.schedule;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;

import com.gtone.mm.core.job.JobModel;
import com.gtone.mm.core.job.JobNode;
import com.gtone.mm.util.MMCommonUtil;

import jspeed.base.jdbc.CacheResultSet;
import site.gtone.util.JdbcConnection;
import site.gtone.util.SQLUtils;

public class UserSyncScheduler implements JobNode{
	private SQLUtils SQL = new SQLUtils();

	@Override
	public void interrupt() {
		// 현재는 단일 JDBC 작업이므로 다음 실행부터 중단 상태를 반영하도록 확장할 수 있다.
	}

	@Override
	public void run(JobModel model) throws Exception {
		model.setChkcnt(userSync());
	}
	
	public long userSync() throws Exception {
		CacheResultSet crs = null;
		HashMap param = new HashMap();
		String uri = "";
		String user = "";
		String pwd = "";
		JdbcConnection jdbcCon = null;
		Connection con = null;
		MMCommonUtil mcu = new MMCommonUtil();
		long syncCount = 0;
		
		try {
			// 내부 DB SQL 매퍼를 초기화한다.
			SQL.setJndiName();

			// 1. 외부 DB 접속정보 조회
			param.put("QUERY_ID", "findLnkInfo");
			param.put("PRDT_VER", "USER_SYNC");
			crs = SQL.SelectQuery(param);
			
			while(crs.next()) {
				uri = crs.getString("URI");
				user = crs.getString("LNK_USER");
				pwd = crs.getString("LNK_USER_PWD");
			}
			
			if (uri.length() == 0 || user.length() == 0) {
				throw new IllegalStateException("외부 DB 접속정보가 없습니다.");
			}
			if(pwd.length()>0) {
				pwd = mcu.decrypt(pwd);
			}
			
			jdbcCon = new JdbcConnection("oracle.jdbc.driver.OracleDriver", uri,user,pwd);
			con = jdbcCon.getConnection();
			
			// 2~4. 외부 직급/부서/사용자 조회 → 내부 임시테이블 초기화 → INSERT
			syncCount += getPosition(con);
			syncCount += getDepartment(con);
			syncCount += getUser(con);
			
		} catch (Exception e) {
			System.err.println("사용자 직급 동기화 중 오류가 발생했습니다.");
			e.printStackTrace();
			throw e;
		} finally {
			if (crs != null) {
				try {
					crs.close();
				} catch (Exception ignore) {
					// 결과셋 정리 실패는 원래 오류를 대체하지 않는다.
				}
			}
			if (con != null) {
				try {
					con.close();
				} catch (Exception ignore) {
					// 연결 정리 실패는 원래 오류를 대체하지 않는다.
				}
			}
		}
		
		return syncCount;
	}

	/** 외부 직급을 조회한 뒤 내부 DB를 초기화하고 조회 건수를 INSERT한다. */
	private long getPosition(Connection con) throws Exception {
		long positionCount = 0;
		String sql = SQL.getSqlByQueryId("site.bnk.UserSync.getPositionInfo");
		PreparedStatement selectStmt = null;
		ResultSet rs = null;
		try {
			// 2. 외부 DB 직급 정보 조회
			selectStmt = con.prepareStatement(sql);
			rs = selectStmt.executeQuery();

			while (rs.next()) {
				positionCount++;
			}
			if (positionCount == 0) {
				return 0;
			}

			// ResultSet은 한 번 순회했으므로 다시 조회해 INSERT한다.
			rs.close();
			rs = null;
			selectStmt.close();
			selectStmt = con.prepareStatement(sql);
			rs = selectStmt.executeQuery();

			// 3. 조회 결과가 있을 때만 내부 직급 테이블을 초기화한다.
			HashMap deleteParam = new HashMap();
			deleteParam.put("QUERY_ID", "site.bnk.UserSync.deleteTmpPositionInfo");
			SQL.executeQuery(deleteParam);

			// 4. 조회된 직급을 내부 DB에 INSERT한다.
			while (rs.next()) {
				HashMap insertParam = new HashMap();
				insertParam.put("QUERY_ID", "site.bnk.UserSync.insertTmpPositionInfo");
				insertParam.put("POSITION_CD", rs.getString("POSITION_CD"));
				insertParam.put("POSITION_NM", rs.getString("POSITION_NM"));
				SQL.executeQuery(insertParam);
			}
			return positionCount;
		} finally {
			if (rs != null) {
				rs.close();
			}
			if (selectStmt != null) {
				selectStmt.close();
			}
		}
	}

	/** 외부 부서 정보를 조회한 뒤 내부 부서 임시테이블을 초기화하고 INSERT한다. */
	private long getDepartment(Connection con) throws Exception {
		long departmentCount = 0;
		String sql = SQL.getSqlByQueryId("site.bnk.UserSync.getDepartmentInfo");
		PreparedStatement selectStmt = null;
		ResultSet rs = null;
		try {
			// 2. 외부 DB 부서 정보 조회
			selectStmt = con.prepareStatement(sql);
			rs = selectStmt.executeQuery();
			while (rs.next()) {
				departmentCount++;
			}
			if (departmentCount == 0) {
				return 0;
			}

			rs.close();
			rs = null;
			selectStmt.close();
			selectStmt = con.prepareStatement(sql);
			rs = selectStmt.executeQuery();

			// 3. 조회 결과가 있을 때만 내부 부서 임시테이블을 초기화한다.
			HashMap deleteParam = new HashMap();
			deleteParam.put("QUERY_ID", "site.bnk.UserSync.deleteTmpDepartmentInfo");
			SQL.executeQuery(deleteParam);

			// 4. 조회된 부서 정보를 내부 DB에 INSERT한다.
			while (rs.next()) {
				HashMap insertParam = new HashMap();
				insertParam.put("QUERY_ID", "site.bnk.UserSync.insertTmpDepartmentInfo");
				insertParam.put("DEPT_CD", rs.getString("DEPT_CD"));
				insertParam.put("DEPT_NM", rs.getString("DEPT_NM"));
				SQL.executeQuery(insertParam);
			}
			return departmentCount;
		} finally {
			if (rs != null) {
				rs.close();
			}
			if (selectStmt != null) {
				selectStmt.close();
			}
		}
	}

	/** 외부 사용자 정보를 조회한 뒤 내부 사용자 임시테이블을 초기화하고 INSERT한다. */
	private long getUser(Connection con) throws Exception {
		long userCount = 0;
		String sql = SQL.getSqlByQueryId("site.bnk.UserSync.getUserInfo");
		PreparedStatement selectStmt = null;
		ResultSet rs = null;
		try {
			// 2. 외부 DB 사용자 정보 조회
			selectStmt = con.prepareStatement(sql);
			rs = selectStmt.executeQuery();
			while (rs.next()) {
				userCount++;
			}
			if (userCount == 0) {
				return 0;
			}

			rs.close();
			rs = null;
			selectStmt.close();
			selectStmt = con.prepareStatement(sql);
			rs = selectStmt.executeQuery();

			// 3. 조회 결과가 있을 때만 내부 사용자 임시테이블을 초기화한다.
			HashMap deleteParam = new HashMap();
			deleteParam.put("QUERY_ID", "site.bnk.UserSync.deleteTmpUserInfo");
			SQL.executeQuery(deleteParam);

			// 4. 조회된 사용자 정보를 내부 DB에 INSERT한다.
			while (rs.next()) {
				HashMap insertParam = new HashMap();
				insertParam.put("QUERY_ID", "site.bnk.UserSync.insertTmpUserInfo");
				insertParam.put("USER_ID", rs.getString("USER_ID"));
				insertParam.put("USER_NM", rs.getString("USER_NM"));
				insertParam.put("DEPT_CD", rs.getString("DEPT_CD"));
				insertParam.put("POSITION_CD", rs.getString("POSITION_CD"));
				SQL.executeQuery(insertParam);
			}
			return userCount;
		} finally {
			if (rs != null) {
				rs.close();
			}
			if (selectStmt != null) {
				selectStmt.close();
			}
		}
	}

}
