package io.paradaux.treasury.mappers.typehandlers;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedJdbcTypes(JdbcType.BINARY)
@MappedTypes(byte[].class)
public class Sha256BinaryTypeHandler extends BaseTypeHandler<byte[]> {
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, byte[] p, JdbcType jdbcType) throws SQLException {
        ps.setBytes(i, p);
    }

    @Override
    public byte[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getBytes(columnName);
    }

    @Override
    public byte[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getBytes(columnIndex);
    }

    @Override
    public byte[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getBytes(columnIndex);
    }
}
