package com.homestay;

import com.homestay.db.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

@Component
// NOTE BACKEND: Chạy các bước nâng cấp database sau khi ứng dụng khởi tạo; thứ tự bên dưới bảo đảm bước sau có bảng/cột cần dùng.
public class HourlyPricingStartup implements ApplicationRunner {
    public HourlyPricingStartup(DatabaseSettings settings) {}
    // NOTE BACKEND: Nâng cấp giá, chuẩn hóa phòng, gán cơ sở, thêm phòng mới, bổ sung tài khoản và chuyển về hai quyền; mỗi migration tự quản lý giao dịch.
    @Override public void run(ApplicationArguments arguments) throws Exception {
        try(var connection=DBConnection.getConnection()){
            HourlyPricingMigration.migrate(connection);
            RoomCatalogMigration.migrate(connection);
            RoomPropertiesMigration.migrate(connection);
            RoomExpansionMigration.migrate(connection);
            AccountRegistrationMigration.migrate(connection);
            TwoRoleMigration.migrate(connection);
        }
        System.out.println("Hourly package pricing schema ready; existing booking prices preserved.");
    }
}
