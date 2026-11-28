-- =====================================================================
-- Emerald: lược đồ cập nhật theo mockup đã chốt (27/11/2026)
-- So với bản trước:
--   apartments : bỏ trạng thái RENTED (chỉ quản lý căn mua đứt)
--   residencies: bỏ cột tenure (không còn chủ sở hữu / người thuê)
--   vehicles   : người sử dụng bắt buộc (màn "Thêm phương tiện")
--   invoices   : thanh toán đủ cả hóa đơn (bỏ paid_amount, bỏ bảng payments),
--                thêm thông tin hủy, cho phép phát hành lại sau khi hủy
--   incidents  : thêm trạng thái CANCELLED, kênh tiếp nhận, người báo "khác";
--                chỉ tiếp nhận sự cố khu vực chung và dịch vụ vận hành (bỏ location,
--                thêm area bắt buộc)
--   thêm view v_invoices để suy ra trạng thái "Quá hạn"
-- =====================================================================

-- 0. Tài khoản : quản lí + cư dân(chủ hộ)

CREATE TABLE users (
                       id                           varchar(36) PRIMARY KEY,
                       role                         varchar(15) NOT NULL CHECK (role IN ('MANAGER', 'RESIDENT')),
                       username                     varchar(20) NOT NULL UNIQUE,            -- do BQL đặt khi cấp tài khoản (thường là số điện thoại)
                       full_name                    varchar(255),                            -- dùng cho tài khoản Ban quản lý
                       must_change_password         boolean NOT NULL DEFAULT true,   -- màn "Đổi mật khẩu lần đầu"
                       status                       varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'LOCKED')),
                       resident_id                  varchar(36) UNIQUE,                   -- FK thêm ở dưới (vòng phụ thuộc)
                       last_login_at                datetime,
                       deleted                      tinyint(1)   DEFAULT 0                 NULL,
                       created_at                   datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL,
                       created_by                   varchar(100)                           NULL,
                       updated_at                   datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
                       updated_by                   varchar(100)                           NULL,
                       CHECK ((role = 'RESIDENT') = (resident_id IS NOT NULL))
);


-- 1. Tòa và căn hộ
-- status do tầng ứng dụng cập nhật khi cư trú thay đổi:
--   occupied  = đang ở (có chủ hộ đang cư trú, chủ hộ cũng là chủ sở hữu)
--   vacant    = trống
--   repairing = đang sửa chữa
-- =====================================================================
CREATE TABLE towers (
                        id                          varchar(36)  PRIMARY KEY ,
                        code                        varchar(10)  NOT NULL,                  -- 'A'
                        name                        varchar(100) NOT NULL,                  -- 'Tòa A'
                        deleted                     tinyint(1)   DEFAULT 0                 NULL,
                        created_at                  datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL,
                        created_by                  varchar(100)                           NULL,
                        updated_at                  datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
                        updated_by                  varchar(100)                           NULL,
                        UNIQUE KEY towers_code_uk (code)
);

CREATE TABLE apartments (
                            id                          varchar(36)  PRIMARY KEY ,
                            tower_id                    varchar(36)  NOT NULL,
                            code                        varchar(20)  NOT NULL,             -- 'A-1205'
                            floor                       smallint     NOT NULL,
                            area_m2                     decimal(6, 2) NOT NULL,
                            bedrooms                    smallint     NOT NULL DEFAULT 0,
                            bathrooms                   smallint     NOT NULL DEFAULT 0,
                            status                      enum('OCCUPIED', 'VACANT', 'REPAIRING') NOT NULL DEFAULT 'VACANT',
                            deleted                     tinyint(1)   DEFAULT 0                 NULL,
                            created_at                  datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL,
                            created_by                  varchar(100)                           NULL,
                            updated_at                  datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
                            updated_by                  varchar(100)                           NULL,
                            UNIQUE KEY apartments_code_uk (code),
                            KEY apartments_tower_floor_idx (tower_id, floor),
                            KEY apartments_status_idx (status),
                            CONSTRAINT fk_apartments_tower FOREIGN KEY (tower_id) REFERENCES towers (id),
                            CONSTRAINT chk_apartments_area CHECK (area_m2 > 0)
);

-- =====================================================================
-- 2. Cư dân và cư trú
-- residents: hồ sơ cá nhân (cả chủ hộ lẫn thành viên).
-- residencies: lịch sử cư trú, không ghi đè, mỗi lần dọn vào/ra là một dòng.
-- Chủ hộ chính là chủ sở hữu căn hộ. Thành viên do chủ hộ tự thêm, không cần duyệt.
-- =====================================================================
CREATE TABLE residents (
                           id                           varchar(36) PRIMARY KEY ,
                           full_name                    varchar(150) NOT NULL,
                           birth_date                   date         NULL,
                           gender                       enum('MALE', 'FEMALE', 'OTHER') NULL,
                           phone                        varchar(20)  NULL,
                           email                        varchar(150) NULL,
                           national_id                  varchar(255) NULL,             -- CCCD, nên mã hóa ở tầng ứng dụng
                           deleted                      tinyint(1)   DEFAULT 0                 NULL,
                           created_at                   datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL,
                           created_by                   varchar(100)                           NULL,
                           updated_at                   datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
                           updated_by                   varchar(100)                           NULL,
                           KEY residents_phone_idx (phone)
);

CREATE TABLE residencies (
                             id                          varchar(36) PRIMARY KEY ,
                             apartment_id                varchar(36) NOT NULL,
                             resident_id                 varchar(36) NOT NULL,
                             role                        enum('HEAD', 'MEMBER') NOT NULL,                          -- chủ hộ / thành viên
                             relation                    enum('SPOUSE', 'CHILD', 'PARENT', 'SIBLING', 'OTHER') NULL,  -- quan hệ với chủ hộ, chỉ dùng cho thành viên
                             start_date                  date NOT NULL,
                             end_date                    date NULL,
                             end_reason                  enum('MOVED_OUT', 'MOVED_APARTMENT', 'OTHER') NULL,
                             deleted                     tinyint(1)   DEFAULT 0                 NULL,
                             created_at                  datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL,
                             created_by                  varchar(100)                           NULL,
                             updated_at                  datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
                             updated_by                  varchar(100)                           NULL,

                             active_head_apartment_id    varchar(36) GENERATED ALWAYS AS
                                 (CASE WHEN role = 'HEAD' AND end_date IS NULL AND IFNULL(deleted, 0) = 0
                                           THEN apartment_id END) STORED,
                             active_resident_id varchar(36) GENERATED ALWAYS AS
                                 (CASE WHEN end_date IS NULL AND IFNULL(deleted, 0) = 0
                                           THEN resident_id END) STORED,
    -- Mỗi căn chỉ có 1 chủ hộ đang cư trú.
                             UNIQUE KEY residencies_one_head_uk (active_head_apartment_id),
    -- Mỗi người chỉ cư trú 1 căn tại một thời điểm.
                             UNIQUE KEY residencies_one_active_uk (active_resident_id),
                             KEY residencies_apartment_idx (apartment_id, end_date),
                             CONSTRAINT fk_residencies_apartment FOREIGN KEY (apartment_id) REFERENCES apartments (id),
                             CONSTRAINT fk_residencies_resident  FOREIGN KEY (resident_id)  REFERENCES residents (id),
                             CONSTRAINT chk_residencies_dates  CHECK (end_date IS NULL OR end_date >= start_date),
                             CONSTRAINT chk_residencies_member CHECK (role = 'HEAD' OR relation IS NOT NULL)
);


-- =====================================================================
-- 3. Phương tiện (gắn với căn hộ, người sử dụng bắt buộc)
-- Chuyển đi: thu hồi thẻ (status = REVOKED) hoặc đổi user_resident_id
-- sang người khác trong căn.
-- =====================================================================

CREATE TABLE vehicle_quotas (
                                vehicle_type                   enum('MOTORBIKE', 'CAR') NOT NULL,
                                max_per_apartment              smallint NOT NULL,
                                deleted                        tinyint(1)   DEFAULT 0                 NULL,
                                created_at                     datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                created_by                     varchar(100)                           NULL,
                                updated_at                     datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
                                updated_by                     varchar(100)                           NULL,
                                PRIMARY KEY (vehicle_type),
                                CONSTRAINT chk_vehicle_quotas_max CHECK (max_per_apartment >= 0)
);

CREATE TABLE vehicles (
                          id                              varchar(36) PRIMARY KEY ,
                          apartment_id                    varchar(36) NOT NULL,
                          user_resident_id                varchar(36) NOT NULL,
                          type                            enum('MOTORBIKE', 'CAR') NOT NULL,
                          plate_number                    varchar(20) NOT NULL,
                          card_number                     varchar(30) NOT NULL,
                          issued_at                       date NOT NULL DEFAULT (CURRENT_DATE),
                          revoked_at                      date NULL,
                          status                          enum('ACTIVE', 'REVOKED') NOT NULL DEFAULT 'ACTIVE',
                          deleted                         tinyint(1)   DEFAULT 0                 NULL,
                          created_at                      datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL,
                          created_by                      varchar(100)                           NULL,
                          updated_at                      datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
                          updated_by                      varchar(100)                           NULL,

    -- Biển số và số thẻ chỉ cần duy nhất trong các xe đang gửi.
                          active_plate_number             varchar(20) GENERATED ALWAYS AS
                              (CASE WHEN status = 'ACTIVE' AND IFNULL(deleted, 0) = 0
                                        THEN plate_number END) STORED,
                          active_card_number              varchar(30) GENERATED ALWAYS AS
                              (CASE WHEN status = 'ACTIVE' AND IFNULL(deleted, 0) = 0
                                        THEN card_number END) STORED,

                          UNIQUE KEY vehicles_plate_active_uk (active_plate_number),
                          UNIQUE KEY vehicles_card_active_uk (active_card_number),
                          KEY vehicles_apartment_idx (apartment_id, status),
                          CONSTRAINT fk_vehicles_apartment FOREIGN KEY (apartment_id)     REFERENCES apartments (id),
                          CONSTRAINT fk_vehicles_user      FOREIGN KEY (user_resident_id) REFERENCES residents (id),
                          CONSTRAINT chk_vehicles_revoked CHECK ((status = 'REVOKED') = (revoked_at IS NOT NULL))
);


-- =====================================================================
-- 4. Biểu giá
-- fee_type:
--   management         phí quản lý, một mức chung cho mọi căn
--   service            phí dịch vụ, chia mức theo diện tích [min, max)
--   parking_motorbike  phí gửi xe máy, tính theo số xe
--   parking_car        phí gửi ô tô, tính theo số xe
-- Mỗi biểu giá có hiệu lực theo khoảng ngày. Đổi giá = thêm dòng mới.
-- MySQL không có ràng buộc EXCLUDE: việc không cho các khoảng diện tích và
-- khoảng ngày chồng lấn nhau do tầng ứng dụng kiểm tra khi lưu biểu giá.
-- =====================================================================
CREATE TABLE fee_tariffs (
                             id                          varchar(36)  PRIMARY KEY ,
                             fee_type                    enum('MANAGEMENT', 'SERVICE', 'PARKING_MOTORBIKE', 'PARKING_CAR') NOT NULL,
                             tier_name                   varchar(50)   NULL,           -- 'Mức 1', chỉ dùng cho phí dịch vụ
                             min_area_m2                 decimal(6, 2) NULL,           -- bao gồm
                             max_area_m2                 decimal(6, 2) NULL,           -- không bao gồm, NULL = không giới hạn
                             amount                      bigint        NOT NULL,
                             effective_from              date          NOT NULL,
                             effective_to                date          NULL,
                             deleted                     tinyint(1)   DEFAULT 0                 NULL,
                             created_at                  datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL,
                             created_by                  varchar(100)                           NULL,
                             updated_at                  datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
                             updated_by                  varchar(100)                           NULL,
                             KEY fee_tariffs_lookup_idx (fee_type, effective_from),
                             CONSTRAINT chk_fee_tariffs_amount CHECK (amount >= 0),
                             CONSTRAINT chk_fee_tariffs_service
                                 CHECK (fee_type <> 'SERVICE' OR (tier_name IS NOT NULL AND min_area_m2 IS NOT NULL)),
                             CONSTRAINT chk_fee_tariffs_area  CHECK (max_area_m2 IS NULL OR max_area_m2 > min_area_m2),
                             CONSTRAINT chk_fee_tariffs_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

-- =====================================================================
-- 5. Kỳ thu phí, hóa đơn
-- Không có hóa đơn nháp: kỳ chỉ được tạo khi phát hành
-- (created_at / created_by của kỳ chính là thời điểm và người phát hành).
-- Chỉ phát hành cho căn có trạng thái khác VACANT (do tầng ứng dụng lọc).
-- Hóa đơn lưu bản chụp (tên người nhận, diện tích, mức, đơn giá) tại lúc
-- phát hành nên đổi biểu giá hoặc đổi chủ hộ sau này không ảnh hưởng.
-- Cư dân thanh toán đủ cả hóa đơn, BQL ghi nhận một lần ngay trên hóa đơn
-- (paid_on, received_by, paid_note), không có bảng thanh toán riêng.
-- Hóa đơn đã phát hành không sửa trực tiếp. Chỉ hủy được hóa đơn chưa thanh
-- toán (bắt buộc có lý do) rồi phát hành lại.
-- Trạng thái "Quá hạn" không lưu, suy ra từ due_date (xem view v_invoices).
-- =====================================================================
CREATE TABLE billing_periods (
                                 id                  varchar(36) PRIMARY KEY ,
                                 year                smallint    NOT NULL,
                                 month               smallint    NOT NULL,
                                 due_date            date        NOT NULL,               -- hạn thanh toán áp dụng cho cả kỳ
                                 deleted             tinyint(1)   DEFAULT 0                 NULL,
                                 created_at          datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                 created_by          varchar(100)                           NULL,
                                 updated_at          datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
                                 updated_by          varchar(100)                           NULL,
                                 UNIQUE KEY billing_periods_ym_uk (year, month),
                                 CONSTRAINT chk_billing_periods_month CHECK (month BETWEEN 1 AND 12)
    );

CREATE TABLE invoices (
                          id                    varchar(36)   PRIMARY KEY ,
                          period_id             varchar(36)   NOT NULL,
                          apartment_id          varchar(36)   NOT NULL,
                          recipient_resident_id varchar(36)   NULL,
                          recipient_name        varchar(150)  NOT NULL,   -- chủ hộ tại thời điểm phát hành
                          area_m2               decimal(6, 2) NOT NULL,   -- bản chụp diện tích
                          service_tier_name     varchar(50)   NULL,       -- bản chụp mức dịch vụ
                          total_amount          bigint        NOT NULL,
                          due_date              date          NOT NULL,
                          status                ENUM('PENDING', 'PAID', 'VOID') NOT NULL DEFAULT 'PENDING',

    -- Ghi nhận thanh toán (chỉ tiền mặt, thu đủ cả hóa đơn)
                          paid_on               date          NULL,       -- ngày thu
                          paid_note             varchar(500)  NULL,       -- ghi chú khi thu
                          received_by           varchar(100)  NULL,       -- nhân viên BQL ghi nhận

    -- Hủy hóa đơn (chỉ khi chưa thanh toán)
                          voided_at             datetime      NULL,
                          void_reason           enum('WRONG_DATA', 'WRONG_APARTMENT', 'OTHER') NULL,
                          void_note             varchar(255)  NULL,

                          deleted      tinyint(1)   DEFAULT 0                 NULL,
                          created_at   datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL,
                          created_by   varchar(100)                           NULL,
                          updated_at   datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
                          updated_by   varchar(100)                           NULL,

    -- Mỗi căn chỉ có 1 hóa đơn còn hiệu lực trong một kỳ. Hóa đơn đã hủy
    -- không chiếm chỗ nên có thể phát hành lại.
                          active_apartment_id   varchar(36) GENERATED ALWAYS AS
                              (CASE WHEN status <> 'VOID' AND IFNULL(deleted, 0) = 0
                                        THEN apartment_id END) STORED,

                          UNIQUE KEY invoices_period_active_apartment_uk (period_id, active_apartment_id),
                          KEY invoices_period_status_idx (period_id, status),
                          KEY invoices_apartment_idx (apartment_id, period_id),
                          KEY invoices_status_due_idx (status, due_date),
                          CONSTRAINT fk_invoices_period    FOREIGN KEY (period_id)             REFERENCES billing_periods (id),
                          CONSTRAINT fk_invoices_apartment FOREIGN KEY (apartment_id)          REFERENCES apartments (id),
                          CONSTRAINT fk_invoices_recipient FOREIGN KEY (recipient_resident_id) REFERENCES residents (id),
                          CONSTRAINT chk_invoices_total CHECK (total_amount >= 0),
                          CONSTRAINT chk_invoices_paid  CHECK ((status = 'PAID') = (paid_on IS NOT NULL)),
                          CONSTRAINT chk_invoices_void  CHECK ((status = 'VOID') = (void_reason IS NOT NULL AND voided_at IS NOT NULL))
);

CREATE TABLE invoice_items (
                               id          varchar(36)  PRIMARY KEY ,
                               invoice_id  varchar(36)  NOT NULL,
                               tariff_id   varchar(36)  NULL,
                               fee_type    enum('MANAGEMENT', 'SERVICE', 'PARKING_MOTORBIKE', 'PARKING_CAR') NOT NULL,
                               description varchar(255) NOT NULL,           -- 'Mức 2 (căn 72 m²)', '2 xe máy'
                               quantity    int          NOT NULL DEFAULT 1,
                               unit_amount bigint       NOT NULL,
                               amount      bigint       NOT NULL,
                               deleted      tinyint(1)   DEFAULT 0                 NULL,
                               created_at   datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL,
                               created_by   varchar(100)                           NULL,
                               updated_at   datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
                               updated_by   varchar(100)                           NULL,
                               KEY invoice_items_invoice_idx (invoice_id),
                               CONSTRAINT fk_invoice_items_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE CASCADE,
                               CONSTRAINT fk_invoice_items_tariff  FOREIGN KEY (tariff_id)  REFERENCES fee_tariffs (id),
                               CONSTRAINT chk_invoice_items_qty    CHECK (quantity >= 0),
                               CONSTRAINT chk_invoice_items_unit   CHECK (unit_amount >= 0),
                               CONSTRAINT chk_invoice_items_amount CHECK (amount = quantity * unit_amount)
);

-- Trạng thái hiển thị: PENDING quá hạn (due_date < hôm nay) hiện là OVERDUE.
CREATE OR REPLACE VIEW v_invoices AS
SELECT i.*,
       CASE
           WHEN i.status = 'PENDING' AND i.due_date < CURRENT_DATE THEN 'OVERDUE'
           ELSE i.status
           END AS display_status
FROM invoices i
WHERE IFNULL(i.deleted, 0) = 0;


-- =====================================================================
-- 6. Yêu cầu sự cố
-- Luồng: NEW -> ACKNOWLEDGED -> IN_PROGRESS -> CLOSED. BQL tự chuyển trạng thái,
--   cư dân chỉ theo dõi. Ghi chú kết quả xử lý nằm trong note của dòng
--   incident_events CLOSED.
-- CANCELLED: cư dân tự hủy khi còn NEW, hoặc BQL hủy (kèm cancel_reason)
--   khi chưa CLOSED.
-- BQL nhập hộ cư dân: channel khác PORTAL, status khởi tạo là ACKNOWLEDGED.
-- BQL chỉ xử lý sự cố ở phần sở hữu chung và dịch vụ vận hành (hành lang,
--   thang máy, hầm xe, điện nước chung...). Sự cố trong căn hộ do chủ hộ tự xử
--   lý: nếu cư dân gửi nhầm, BQL hủy với cancel_reason = OUT_OF_SCOPE.
-- apartment_id là căn của người báo (để biết chủ hộ nào xác nhận), khu vực
--   sự cố ghi ở area (vd 'Hành lang tầng 12', 'Thang máy số 2').
-- Người phụ trách là tên (nhân viên hoặc nhà thầu), không có tài khoản.
-- incident_events là dòng tiến độ hiển thị trong chi tiết yêu cầu.
-- =====================================================================

CREATE TABLE incidents (
                           id                      varchar(36)  PRIMARY KEY ,
                           apartment_id            varchar(36)  NOT NULL,
                           reported_by             varchar(36)  NULL,        -- NULL khi BQL nhập người báo là "Khác"
                           reporter_name           varchar(150) NOT NULL,    -- bản chụp tên người báo
                           channel                 enum('PORTAL', 'PHONE', 'IN_PERSON', 'OTHER') NOT NULL DEFAULT 'PORTAL',
                           title                   varchar(200) NOT NULL,
                           description             varchar(255)         NULL,
                           area                    varchar(150) NOT NULL,    -- khu vực / hạng mục: 'Hành lang tầng 12', 'Thang máy số 2'
                           status                  enum('NEW', 'ACKNOWLEDGED', 'IN_PROGRESS', 'CLOSED', 'CANCELLED')
        NOT NULL DEFAULT 'NEW',
                           assignee_name           varchar(150) NULL,
                           closed_at               datetime     NULL,
                           cancelled_at            datetime     NULL,
                           cancel_reason           enum('MISTAKE', 'DUPLICATE', 'OUT_OF_SCOPE', 'OTHER') NULL,  -- lý do BQL chọn khi hủy
                           deleted                 tinyint(1)   DEFAULT 0                 NULL,
                           created_at              datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL,
                           created_by              varchar(100)                           NULL,
                           updated_at              datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
                           updated_by              varchar(100)                           NULL,
                           KEY incidents_apartment_idx (apartment_id, created_at),
                           KEY incidents_status_idx (status, created_at),
                           CONSTRAINT fk_incidents_apartment FOREIGN KEY (apartment_id) REFERENCES apartments (id),
                           CONSTRAINT fk_incidents_reporter  FOREIGN KEY (reported_by)  REFERENCES residents (id),
                           CONSTRAINT chk_incidents_closed    CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL)),
                           CONSTRAINT chk_incidents_cancelled CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL)),
                           CONSTRAINT chk_incidents_cancel_reason CHECK (cancel_reason IS NULL OR status = 'CANCELLED')
);

-- created_at của incident_attachments chính là thời điểm tải lên.
CREATE TABLE incident_attachments (
                                      id                      varchar(36)  PRIMARY KEY ,
                                      incident_id             varchar(36)  NOT NULL,
                                      storage_key             varchar(500) NOT NULL,          -- URL đầy đủ của ảnh trên kho lưu trữ (S3, ...)
                                      deleted                 tinyint(1)   DEFAULT 0                 NULL,
                                      created_at              datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                      created_by              varchar(100)                           NULL,
                                      updated_at              datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
                                      updated_by              varchar(100)                           NULL,
                                      KEY incident_attachments_incident_idx (incident_id),
                                      CONSTRAINT fk_incident_attachments_incident FOREIGN KEY (incident_id)
                                          REFERENCES incidents (id) ON DELETE CASCADE
);

-- created_by của incident_events chính là người thực hiện bước tiến độ.
CREATE TABLE incident_events (
                                 id                      varchar(36)  PRIMARY KEY ,
                                 incident_id             varchar(36)  NOT NULL,
                                 status                  enum('NEW','ACKNOWLEDGED','IN_PROGRESS','CLOSED','CANCELLED') NOT NULL,
                                 assignee_name           varchar(150) NULL,
                                 note                    varchar(255)         NULL,
                                 deleted                 tinyint(1)   DEFAULT 0                 NULL,
                                 created_at              datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                 created_by              varchar(100)                           NULL,
                                 updated_at              datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
                                 updated_by              varchar(100)                           NULL,
                                 KEY incident_events_incident_idx (incident_id, created_at),
                                 CONSTRAINT fk_incident_events_incident FOREIGN KEY (incident_id)
                                     REFERENCES incidents (id) ON DELETE CASCADE
);

SHOW CREATE TABLE emerald_system.users;
