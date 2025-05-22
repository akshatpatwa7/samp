package com.function.controller;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;

import java.sql.*;
import java.util.*;

public class OracleConnectFunction {
    @FunctionName("ConnectToOracle")
    public HttpResponseMessage run(
        @HttpTrigger(name = "req", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.FUNCTION) 
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context) {

        String url = "jdbc:oracle:thin:@//ldbilldbprod.worldbank.org:12106/LDBILL.worldbank.org";
        String username = "DOCU_SIGN";
        String password = "D03nFvARt!981gp";

        try {
            // Load Oracle JDBC driver
            Class.forName("oracle.jdbc.OracleDriver");

            // Connect to Oracle
            Connection conn = DriverManager.getConnection(url, username, password);

            // Execute test query
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT 1 FROM DUAL");

            if (rs.next()) {
                return request.createResponseBuilder(HttpStatus.OK)
                    .body("Oracle connection successful! Value from DB: " + rs.getInt(1))
                    .build();
            } else {
                return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Connected but query failed.")
                    .build();
            }

        } catch (Exception e) {
            context.getLogger().severe("Oracle JDBC connection failed: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error: " + e.getMessage())
                .build();
        }
    }
}
