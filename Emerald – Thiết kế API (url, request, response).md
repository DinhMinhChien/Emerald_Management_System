# Emerald – Thiết kế API (url, request, response)

Dựa trên mockup đã chốt 27/11/2026, emerald\_schema.sql và tài liệu Controller và endpoint. Không gồm login, logout, refresh token (AuthController đã làm). Cuối tài liệu có mục các điểm tôi suy ra hoặc đề xuất thêm để bạn duyệt.

## 0. Quy ước chung

- Base URL: `/api/v1`. Mọi request kèm `Authorization: Bearer <JWT>`. `/manager/**` yêu cầu role MANAGER, `/resident/**` yêu cầu role RESIDENT.
- Request và response là JSON, mỗi field một dòng. Giá trị trong ví dụ chỉ để minh họa kiểu dữ liệu (id thật là chuỗi UUID). Field nào không bắt buộc, có thể null hoặc có giới hạn độ dài được ghi ở mục Quy tắc ngay dưới ví dụ.
- Tên field camelCase. Tiền là số nguyên VND. Ngày `yyyy-MM-dd`, ngày giờ ISO-8601.
- Danh sách phân trang: query `page` (bắt đầu từ 0, mặc định 0), `size` (mặc định 20, tối đa 100). Response luôn có dạng `items`, `total`, `page`, `size`. Tìm kiếm dùng `q`.
- Người thực hiện (created\_by, updated\_by, received\_by) lấy từ JWT, không nhận từ body. Nhóm `/resident/**` lấy căn hộ từ JWT, không nhận apartmentId.
- Thành công: GET 200, POST tạo mới 201, POST hành động 200 kèm đối tượng sau khi cập nhật, không có nội dung thì 204. Không có DELETE (xóa mềm).
- Mọi lỗi có dạng dưới đây. Mã HTTP: 400 sai định dạng (VALIDATION\_ERROR), 401 chưa đăng nhập, 403 sai role, 404 không thấy, 409 trùng hoặc sai trạng thái, 422 vi phạm quy tắc nghiệp vụ. Danh sách mã lỗi ở mục 12.

```json
{
  "code": "APARTMENT_CODE_DUPLICATED",
  "message": "Mã căn A-1205 đã tồn tại",
  "details": [
    {
      "field": "code",
      "message": "Đã tồn tại"
    }
  ]
}
```

- CCCD luôn trả dạng che (`nationalIdMasked`, ví dụ 012\*\*\*\*\*789). Số điện thoại trả đầy đủ cho BQL.

## 1. Enum

- `ApartmentStatus`: OCCUPIED | VACANT | REPAIRING
- `ResidencyRole`: HEAD | MEMBER
- `Relation`: SPOUSE | CHILD | PARENT | SIBLING | OTHER
- `EndReason`: MOVED\_OUT | MOVED\_APARTMENT | OTHER
- `Gender`: MALE | FEMALE | OTHER
- `AccountStatus`: ACTIVE | LOCKED. Response có thêm NONE cho cư dân chưa có tài khoản (mọi thành viên)
- `VehicleType`: MOTORBIKE | CAR
- `VehicleStatus`: ACTIVE | REVOKED
- `FeeType`: MANAGEMENT | SERVICE | PARKING\_MOTORBIKE | PARKING\_CAR
- `InvoiceStatus`: PENDING | PAID | VOID. Field `displayStatus` thêm OVERDUE (PENDING có due\_date nhỏ hơn hôm nay)
- `VoidReason`: WRONG\_DATA | WRONG\_APARTMENT | OTHER
- `IncidentStatus`: NEW | ACKNOWLEDGED | IN\_PROGRESS | CLOSED | CANCELLED
- `IncidentChannel`: PORTAL | PHONE | IN\_PERSON | OTHER
- `CancelReason`: MISTAKE | DUPLICATE | OUT\_OF\_SCOPE | OTHER

## 2. Quy tắc tài khoản: chỉ chủ hộ có tài khoản

- Chỉ chủ hộ (residency role HEAD) có dòng `users` và đăng nhập được cổng cư dân. Thành viên (MEMBER) không có tài khoản, chỉ là hồ sơ.
- Vì vậy mọi endpoint `/resident/**` đều do chủ hộ gọi. Không cần field `isHead` và không có lỗi NOT\_HEAD như bản trước.
- Khi chủ hộ chuyển đi (move-out), service khóa tài khoản của chủ hộ cũ (status LOCKED, khóa luôn trên Keycloak). Chủ hộ mới được cấp tài khoản trong cùng giao dịch.
- Tài khoản mà không còn cư trú đang hoạt động gọi `/resident/**` nhận 403 NO\_ACTIVE\_RESIDENCY.
- `accountStatus` trong danh sách cư dân là NONE với mọi thành viên.

## 3. Endpoint dùng chung

### 3.1 GET /me

Màn hình: **Đổi mật khẩu lần đầu** (đọc `mustChangePassword` để biết có phải chuyển sang màn này sau khi đăng nhập không). Thanh header của mọi màn BQL ("Ban quản lý · Trần Văn Q") và cư dân ("Nguyễn Văn An · A-1205"). `role` cho frontend chọn giao diện BQL hay cư dân.

Response 200:

```json
{
  "role": "RESIDENT",
  "fullName": "Nguyễn Văn An",
  "mustChangePassword": false,
  "apartmentCode": "A-1205"
}
```

Quy tắc: `apartmentCode` là null với tài khoản MANAGER. Tài khoản LOCKED đã bị Keycloak chặn đăng nhập nên không cần trả `status`. Tên đăng nhập lấy từ `account.username` trong `GET /resident/profile` (11.2), thời điểm đăng nhập gần nhất lấy từ `account.lastLoginAt` trong 6.2.

### 3.2 POST /me/change-password

Dùng cho màn Đổi mật khẩu lần đầu và nút Đổi mật khẩu ở Hồ sơ của tôi. Đổi trên Keycloak, thành công thì đặt `must_change_password = false`.

Request body:

```json
{
  "currentPassword": "Tam@12345",
  "newPassword": "MatKhauMoi@2026"
}
```

Response 204, không có nội dung.

Lỗi: 422 WRONG\_CURRENT\_PASSWORD, 422 WEAK\_PASSWORD, 422 SAME\_PASSWORD.

### 3.3 GET /towers

Dùng cho ô lọc Tất cả tòa và form Thêm căn hộ. Không phân trang.

Response 200:

```json
{
  "items": [
    {
      "id": "twr_a",
      "code": "A",
      "name": "Tòa A"
    }
  ]
}
```

## 4. DashboardController

### 4.1 GET /manager/dashboard

Một lần trả đủ dữ liệu cho màn Tổng quan. Kỳ hiện tại là kỳ phát hành gần nhất.

Response 200:

```json
{
  "period": {
    "id": "per_202611",
    "year": 2026,
    "month": 11
  },
  "occupancy": {
    "occupied": 172,
    "total": 200,
    "rate": 86.0
  },
  "collection": {
    "collectedAmount": 412500000,
    "totalAmount": 689000000,
    "rate": 59.9
  },
  "overdueApartments": 7,
  "incidents": {
    "needHandling": 9,
    "new": 3,
    "acknowledged": 4,
    "inProgress": 2
  },
  "todo": {
    "pendingInvoices": 64,
    "overdueInvoices": 7,
    "unacknowledgedIncidents": 3
  },
  "latestIncidents": [
    {
      "id": "inc_01",
      "title": "Thang máy số 2 kêu to",
      "area": "Thang máy số 2",
      "apartmentCode": "A-1205",
      "status": "NEW",
      "createdAt": "2026-11-27T07:40:00+07:00"
    }
  ],
  "recentInvoices": [
    {
      "id": "inv_01",
      "apartmentCode": "A-1205",
      "recipientName": "Nguyễn Văn An",
      "totalAmount": 3450000,
      "displayStatus": "PENDING",
      "dueDate": "2026-12-10"
    }
  ]
}
```

Quy tắc:

- `rate` là phần trăm 0 đến 100, làm tròn một chữ số thập phân. `period` là null nếu chưa phát hành kỳ nào.
- `occupancy.occupied` là số căn OCCUPIED, `total` là tổng số căn chưa xóa.
- `collection.collectedAmount` là tổng hóa đơn PAID của kỳ, `totalAmount` là tổng hóa đơn không VOID của kỳ.
- `overdueApartments` là số căn có ít nhất một hóa đơn displayStatus OVERDUE.
- `needHandling` = new + acknowledged + inProgress. `unacknowledgedIncidents` = new.
- `pendingInvoices` là PENDING chưa quá hạn, `overdueInvoices` là PENDING quá hạn (cùng định nghĩa với `/manager/invoices/summary`).
- `latestIncidents` và `recentInvoices` mỗi danh sách tối đa 3 dòng, sắp xếp created\_at giảm dần.

## 5. ApartmentController

### 5.1 GET /manager/apartments

Query: `q` (mã căn, tên chủ hộ), `towerId`, `status`, `hasHead` (true hoặc false, dùng cho ô Căn hộ chưa có chủ hộ khi thêm chủ hộ), `page`, `size`.

Response 200:

```json
{
  "items": [
    {
      "id": "apt_01",
      "code": "A-1205",
      "tower": {
        "id": "twr_a",
        "code": "A",
        "name": "Tòa A"
      },
      "floor": 12,
      "areaM2": 72.5,
      "status": "OCCUPIED",
      "head": {
        "residentId": "res_01",
        "fullName": "Nguyễn Văn An",
        "phone": "0901234567"
      },
      "debtAmount": 3450000,
      "overdueInvoiceCount": 0
    }
  ],
  "total": 200,
  "page": 0,
  "size": 20
}
```

Quy tắc: `head` là null nếu căn chưa có chủ hộ. `debtAmount` là tổng `total_amount` các hóa đơn PENDING của căn (gồm cả quá hạn).

### 5.2 POST /manager/apartments

Trạng thái ban đầu VACANT. Mã căn phải duy nhất.

Request body:

```json
{
  "towerId": "twr_a",
  "code": "A-1205",
  "floor": 12,
  "areaM2": 72.5,
  "bedrooms": 2,
  "bathrooms": 2
}
```

Response 201: cùng dạng 5.3, với `status` là VACANT, `head` là null.

Lỗi: 404 TOWER\_NOT\_FOUND, 409 APARTMENT\_CODE\_DUPLICATED, 400 VALIDATION\_ERROR (areaM2 phải lớn hơn 0).

### 5.3 GET /manager/apartments/{id}

Tab Tổng quan: thông tin căn, chủ hộ hiện tại, công nợ và lịch sử cư trú.

Response 200:

```json
{
  "id": "apt_01",
  "code": "A-1205",
  "tower": {
    "id": "twr_a",
    "code": "A",
    "name": "Tòa A"
  },
  "floor": 12,
  "areaM2": 72.5,
  "bedrooms": 2,
  "bathrooms": 2,
  "status": "OCCUPIED",
  "serviceTierName": "Mức 2",
  "head": {
    "residentId": "res_01",
    "fullName": "Nguyễn Văn An",
    "phone": "0901234567",
    "since": "2024-03-01"
  },
  "occupantCount": 4,
  "debt": {
    "totalAmount": 3450000,
    "pendingInvoiceCount": 1,
    "overdueInvoiceCount": 0,
    "overdueAmount": 0
  },
  "residencyHistory": [
    {
      "residencyId": "rsd_01",
      "resident": {
        "id": "res_01",
        "fullName": "Nguyễn Văn An"
      },
      "role": "HEAD",
      "relation": null,
      "startDate": "2024-03-01",
      "endDate": null,
      "endReason": null
    }
  ]
}
```

Quy tắc: `serviceTierName` là mức dịch vụ theo diện tích căn từ biểu giá đang hiệu lực. `residencyHistory` sắp xếp `startDate` giảm dần. Lỗi: 404 APARTMENT\_NOT\_FOUND.

### 5.4 GET /manager/apartments/{id}/residencies

Tab Cư trú. Query: `current` (true thì chỉ lấy người đang cư trú, tức end\_date IS NULL). Không phân trang.

Response 200:

```json
{
  "items": [
    {
      "residencyId": "rsd_02",
      "resident": {
        "id": "res_02",
        "fullName": "Nguyễn Thị Hoa",
        "birthDate": "1990-05-12",
        "gender": "FEMALE",
        "phone": null,
        "hasAccount": false
      },
      "role": "MEMBER",
      "relation": "SPOUSE",
      "startDate": "2024-03-01",
      "endDate": null,
      "endReason": null
    }
  ]
}
```

### 5.5 GET /manager/apartments/{id}/vehicles

Tab Phương tiện. Query: `status` (mặc định ACTIVE, truyền ALL để lấy cả thẻ đã thu hồi).

Response 200:

```json
{
  "quotas": [
    {
      "type": "MOTORBIKE",
      "max": 3,
      "used": 2
    },
    {
      "type": "CAR",
      "max": 1,
      "used": 0
    }
  ],
  "parkingFee": {
    "motorbike": {
      "count": 2,
      "unitAmount": 100000,
      "amount": 200000
    },
    "car": {
      "count": 0,
      "unitAmount": 1200000,
      "amount": 0
    },
    "totalAmount": 200000
  },
  "items": [
    {
      "id": "veh_01",
      "type": "MOTORBIKE",
      "plateNumber": "29B1-123.45",
      "cardNumber": "MB-000123",
      "user": {
        "id": "res_01",
        "fullName": "Nguyễn Văn An"
      },
      "issuedAt": "2024-03-05",
      "revokedAt": null,
      "status": "ACTIVE"
    }
  ]
}
```

Quy tắc: `used` là số xe ACTIVE của căn theo loại. `parkingFee` là phí gửi xe tháng này, tính theo số xe ACTIVE và đơn giá đang hiệu lực.

Tab Hóa đơn và tab Sự cố của căn hộ dùng lại `GET /manager/invoices?apartmentId=` và `GET /manager/incidents?apartmentId=`.

## 6. ResidentController

### 6.1 GET /manager/residents

Query: `q` (họ tên, số điện thoại), `towerId`, `residing` (mặc định true, chỉ lấy người đang cư trú; false để gồm cả người đã chuyển đi), `page`, `size`.

Response 200:

```json
{
  "items": [
    {
      "id": "res_02",
      "fullName": "Nguyễn Thị Hoa",
      "phone": null,
      "apartment": {
        "id": "apt_01",
        "code": "A-1205",
        "towerCode": "A",
        "towerName": "Tòa A"
      },
      "role": "MEMBER",
      "relation": "SPOUSE",
      "accountStatus": "NONE"
    }
  ],
  "total": 410,
  "page": 0,
  "size": 20
}
```

Quy tắc:

- `relation` là quan hệ với chủ hộ, null nếu là chủ hộ. `apartment` và `role` lấy từ cư trú đang hoạt động, null nếu đã chuyển đi.
- `accountStatus` là NONE với mọi thành viên, chỉ chủ hộ mới là ACTIVE hoặc LOCKED.

### 6.2 GET /manager/residents/{id}

Response 200:

```json
{
  "id": "res_01",
  "fullName": "Nguyễn Văn An",
  "birthDate": "1985-09-20",
  "gender": "MALE",
  "phone": "0901234567",
  "email": "an.nguyen@example.com",
  "nationalIdMasked": "012*****789",
  "account": {
    "userId": "usr_01",
    "username": "0901234567",
    "status": "ACTIVE",
    "mustChangePassword": false,
    "createdAt": "2024-03-01T09:00:00+07:00",
    "lastLoginAt": "2026-11-27T08:15:00+07:00"
  },
  "currentResidency": {
    "residencyId": "rsd_01",
    "apartment": {
      "id": "apt_01",
      "code": "A-1205",
      "towerCode": "A",
      "towerName": "Tòa A"
    },
    "role": "HEAD",
    "relation": null,
    "startDate": "2024-03-01",
    "housemates": [
      {
        "id": "res_02",
        "fullName": "Nguyễn Thị Hoa",
        "relation": "SPOUSE"
      }
    ]
  },
  "residencyHistory": [
    {
      "residencyId": "rsd_01",
      "apartment": {
        "id": "apt_01",
        "code": "A-1205",
        "towerCode": "A",
        "towerName": "Tòa A"
      },
      "role": "HEAD",
      "relation": null,
      "startDate": "2024-03-01",
      "endDate": null,
      "endReason": null
    }
  ]
}
```

Quy tắc: `account` là null với thành viên. `currentResidency` là null nếu đã chuyển đi. Lỗi: 404 RESIDENT\_NOT\_FOUND.

### 6.3 POST /manager/residents/heads

Màn Thêm chủ hộ. Một giao dịch: tạo `residents`, tạo `residencies` role HEAD, tạo `users` (role RESIDENT, must\_change\_password = true), tạo user trên Keycloak, đặt căn OCCUPIED.

Request body:

```json
{
  "fullName": "Nguyễn Văn An",
  "birthDate": "1985-09-20",
  "gender": "MALE",
  "phone": "0901234567",
  "email": "an.nguyen@example.com",
  "nationalId": "012345678789",
  "apartmentId": "apt_01",
  "startDate": "2024-03-01",
  "username": "0901234567",
  "temporaryPassword": "Tam@12345"
}
```

Response 201:

```json
{
  "residentId": "res_01",
  "residencyId": "rsd_01",
  "userId": "usr_01",
  "username": "0901234567",
  "apartmentId": "apt_01",
  "apartmentStatus": "OCCUPIED"
}
```

Quy tắc:

- Bắt buộc: `fullName`, `apartmentId`, `startDate`, `username` (tối đa 20 ký tự, thường là số điện thoại), `temporaryPassword`. Các field còn lại không bắt buộc.
- Lỗi: 404 APARTMENT\_NOT\_FOUND, 409 APARTMENT\_ALREADY\_HAS\_HEAD, 409 USERNAME\_DUPLICATED, 409 RESIDENT\_ALREADY\_RESIDING, 422 WEAK\_PASSWORD, 422 APARTMENT\_REPAIRING, 502 IDENTITY\_PROVIDER\_ERROR.
- Tạo user trên Keycloak lỗi thì rollback toàn bộ giao dịch DB và trả 502.

### 6.4 PUT /manager/residents/{id}

Màn Chỉnh sửa hồ sơ. Gửi toàn bộ field cho phép sửa.

Request body:

```json
{
  "fullName": "Nguyễn Văn An",
  "birthDate": "1985-09-20",
  "gender": "MALE",
  "phone": "0901234567",
  "email": "an.nguyen@example.com",
  "nationalId": null
}
```

Response 200: cùng dạng 6.2.

Quy tắc: chỉ `fullName` bắt buộc. `nationalId` là null hoặc bỏ trống nghĩa là giữ nguyên CCCD hiện có (vì response chỉ trả dạng che). Lỗi: 404 RESIDENT\_NOT\_FOUND, 400 VALIDATION\_ERROR.

### 6.5 POST /manager/residents/{id}/account/reset-password

Đặt lại mật khẩu trên Keycloak và đặt `must_change_password = true`. Không gửi `temporaryPassword` thì backend tự sinh và trả lại đúng một lần (không lưu, không ghi log).

Request body:

```json
{
  "temporaryPassword": "Tam@67890"
}
```

Response 200:

```json
{
  "userId": "usr_01",
  "temporaryPassword": "Tam@67890",
  "mustChangePassword": true
}
```

Lỗi: 404 RESIDENT\_NOT\_FOUND, 422 NO\_ACCOUNT (thành viên không có tài khoản), 422 WEAK\_PASSWORD, 409 ACCOUNT\_LOCKED.

### 6.6 POST /manager/residents/{id}/account/lock

Đặt `users.status = LOCKED` và khóa user trên Keycloak. Không có body.

Response 200:

```json
{
  "userId": "usr_01",
  "status": "LOCKED"
}
```

Lỗi: 404 RESIDENT\_NOT\_FOUND, 422 NO\_ACCOUNT, 409 ACCOUNT\_ALREADY\_LOCKED.

### 6.7 POST /manager/residencies/{residencyId}/move-out

Ghi nhận chuyển đi, dùng cho cả chủ hộ lẫn thành viên. Một giao dịch.

Request body:

```json
{
  "endDate": "2026-11-30",
  "endReason": "MOVED_OUT",
  "newHead": {
    "residentId": "res_02",
    "username": "0912345678",
    "temporaryPassword": "Tam@12345"
  },
  "vehicleActions": [
    {
      "vehicleId": "veh_01",
      "action": "TRANSFER",
      "transferToResidentId": "res_02"
    },
    {
      "vehicleId": "veh_02",
      "action": "REVOKE"
    }
  ]
}
```

Response 200:

```json
{
  "residencyId": "rsd_01",
  "resident": {
    "id": "res_01",
    "fullName": "Nguyễn Văn An"
  },
  "endDate": "2026-11-30",
  "endReason": "MOVED_OUT",
  "apartment": {
    "id": "apt_01",
    "code": "A-1205",
    "status": "OCCUPIED"
  },
  "oldHeadAccountLocked": true,
  "newHead": {
    "residentId": "res_02",
    "residencyId": "rsd_02",
    "accountCreated": true,
    "username": "0912345678"
  },
  "vehicleResults": [
    {
      "vehicleId": "veh_01",
      "action": "TRANSFER",
      "transferToResidentId": "res_02"
    },
    {
      "vehicleId": "veh_02",
      "action": "REVOKE",
      "transferToResidentId": null
    }
  ]
}
```

Quy tắc:

- `endDate`, `endReason` bắt buộc. `newHead` chỉ gửi khi người rời đi là chủ hộ và căn còn người ở; `newHead.username` và `temporaryPassword` bắt buộc vì thành viên chưa có tài khoản. `vehicleActions` cần một dòng cho mỗi thẻ xe ACTIVE của người rời đi. Response có `newHead` là null khi không đổi chủ hộ, `oldHeadAccountLocked` là false khi người rời đi là thành viên.
- 404 RESIDENCY\_NOT\_FOUND. 409 RESIDENCY\_ALREADY\_ENDED nếu đã có end\_date. 422 INVALID\_END\_DATE nếu endDate nhỏ hơn start\_date.
- Chủ hộ rời đi mà căn còn người ở: 422 NEW\_HEAD\_REQUIRED. `newHead.residentId` phải là thành viên đang ở cùng căn, không thì 422 NEW\_HEAD\_INVALID. Thiếu username hoặc temporaryPassword thì 422 NEW\_HEAD\_ACCOUNT\_REQUIRED. Trùng username thì 409 USERNAME\_DUPLICATED.
- Thiếu xử lý thẻ xe: 422 VEHICLE\_ACTION\_REQUIRED (details liệt kê vehicleId còn thiếu). `transferToResidentId` phải là người đang cư trú cùng căn và khác người rời đi, không thì 422 TRANSFER\_TARGET\_INVALID.
- Sau xử lý: không còn ai cư trú thì căn chuyển VACANT, còn người thì giữ OCCUPIED.
- Chủ hộ rời đi: kết thúc cư trú chủ hộ cũ trước rồi mới đặt chủ hộ mới (khóa duy nhất một chủ hộ đang cư trú), đồng thời khóa tài khoản chủ hộ cũ.

## 7. VehicleController

### 7.1 POST /manager/apartments/{apartmentId}/vehicles

Màn Thêm phương tiện. Biển số được chuẩn hóa (trim, viết hoa) trước khi lưu và so trùng.

Request body:

```json
{
  "type": "MOTORBIKE",
  "plateNumber": "29B1-123.45",
  "cardNumber": "MB-000123",
  "userResidentId": "res_01",
  "issuedAt": "2026-11-27"
}
```

Response 201:

```json
{
  "id": "veh_01",
  "type": "MOTORBIKE",
  "plateNumber": "29B1-123.45",
  "cardNumber": "MB-000123",
  "user": {
    "id": "res_01",
    "fullName": "Nguyễn Văn An"
  },
  "issuedAt": "2026-11-27",
  "revokedAt": null,
  "status": "ACTIVE"
}
```

Quy tắc:

- Bắt buộc: `type`, `plateNumber`, `cardNumber`, `userResidentId` (người đang cư trú tại căn). `issuedAt` không bắt buộc, mặc định hôm nay.
- Lỗi: 404 APARTMENT\_NOT\_FOUND, 422 USER\_NOT\_IN\_APARTMENT, 422 VEHICLE\_QUOTA\_EXCEEDED (details có type, max, used), 422 ISSUED\_AT\_IN\_FUTURE, 409 PLATE\_NUMBER\_DUPLICATED, 409 CARD\_NUMBER\_DUPLICATED.
- Hai lỗi 409 chỉ xét trong các xe ACTIVE. DB có khóa duy nhất nên service phải bắt lỗi tranh chấp và trả 409.

### 7.2 POST /manager/vehicles/{id}/revoke

Thu hồi thẻ (status = REVOKED, revoked\_at).

Request body:

```json
{
  "revokedAt": "2026-11-27"
}
```

Response 200: cùng dạng 7.1, với `status` là REVOKED và `revokedAt` có giá trị.

Quy tắc: `revokedAt` không bắt buộc, mặc định hôm nay; không được nhỏ hơn `issuedAt` và không ở tương lai. Lỗi: 404 VEHICLE\_NOT\_FOUND, 409 VEHICLE\_ALREADY\_REVOKED, 422 INVALID\_REVOKED\_DATE. Hóa đơn đã phát hành không đổi vì có bản chụp.

## 8. BillingController (kỳ thu phí)

### 8.1 GET /manager/billing-periods/available-months

Các tháng chưa có kỳ, cho ô chọn Tháng ở màn Tạo hóa đơn tháng.

Response 200:

```json
{
  "defaultMonth": {
    "year": 2026,
    "month": 12
  },
  "items": [
    {
      "year": 2026,
      "month": 12,
      "label": "12/2026"
    },
    {
      "year": 2027,
      "month": 1,
      "label": "01/2027"
    }
  ]
}
```

Quy tắc: `defaultMonth` là tháng kế sau kỳ gần nhất đã phát hành, null nếu chưa có kỳ nào (khi đó dùng tháng hiện tại). Phạm vi đề xuất: các tháng chưa có kỳ trong 12 tháng gần nhất, cộng tháng hiện tại và tháng kế sau.

### 8.2 GET /manager/billing-periods/preview?year=&month=

Xem trước trước khi phát hành, không ghi dữ liệu. Query thêm: `q` (mã căn), `towerId`, `page`, `size`. Các số tổng là của toàn kỳ, không phụ thuộc bộ lọc hay trang.

Response 200:

```json
{
  "year": 2026,
  "month": 12,
  "invoiceCount": 172,
  "skippedVacantCount": 28,
  "totalAmount": 593400000,
  "warnings": [
    {
      "apartmentCode": "B-0305",
      "code": "NO_ACTIVE_HEAD",
      "message": "Căn không có chủ hộ đang cư trú, sẽ bị bỏ qua"
    }
  ],
  "apartments": {
    "items": [
      {
        "apartmentId": "apt_01",
        "code": "A-1205",
        "towerCode": "A",
        "areaM2": 72.5,
        "serviceTierName": "Mức 2",
        "apartmentStatus": "OCCUPIED",
        "recipientName": "Nguyễn Văn An",
        "items": [
          {
            "feeType": "MANAGEMENT",
            "description": "Phí quản lý",
            "quantity": 1,
            "unitAmount": 1200000,
            "amount": 1200000
          },
          {
            "feeType": "SERVICE",
            "description": "Mức 2 (căn 72.5 m²)",
            "quantity": 1,
            "unitAmount": 2050000,
            "amount": 2050000
          },
          {
            "feeType": "PARKING_MOTORBIKE",
            "description": "2 xe máy",
            "quantity": 2,
            "unitAmount": 100000,
            "amount": 200000
          }
        ],
        "totalAmount": 3450000
      }
    ],
    "total": 172,
    "page": 0,
    "size": 20
  }
}
```

Quy tắc: `warnings` dùng cho căn sẽ bị bỏ qua dù không VACANT (ví dụ đang sửa chữa mà không có chủ hộ, mã NO\_ACTIVE\_HEAD). Lỗi: 409 PERIOD\_ALREADY\_EXISTS, 422 TARIFF\_NOT\_CONFIGURED, 400 VALIDATION\_ERROR.

### 8.3 GET /manager/fee-tariffs/current

Thẻ Đơn giá áp dụng. Lấy các biểu giá có `effective_from` nhỏ hơn hoặc bằng hôm nay và (`effective_to` null hoặc lớn hơn hoặc bằng hôm nay).

Response 200:

```json
{
  "asOf": "2026-11-27",
  "management": {
    "tariffId": "trf_01",
    "amount": 1200000
  },
  "serviceTiers": [
    {
      "tariffId": "trf_02",
      "tierName": "Mức 1",
      "minAreaM2": 0,
      "maxAreaM2": 60,
      "amount": 1500000
    },
    {
      "tariffId": "trf_03",
      "tierName": "Mức 2",
      "minAreaM2": 60,
      "maxAreaM2": null,
      "amount": 2050000
    }
  ],
  "parkingMotorbike": {
    "tariffId": "trf_04",
    "amount": 100000
  },
  "parkingCar": {
    "tariffId": "trf_05",
    "amount": 1200000
  },
  "vehicleQuotas": [
    {
      "type": "MOTORBIKE",
      "maxPerApartment": 3
    },
    {
      "type": "CAR",
      "maxPerApartment": 1
    }
  ]
}
```

Quy tắc: mỗi nhóm phí là null nếu chưa có biểu giá hiệu lực. `maxAreaM2` là null nghĩa là không giới hạn.

### 8.4 POST /manager/billing-periods

Phát hành kỳ. Một giao dịch. Kỳ chỉ được tạo lúc phát hành nên `created_at`, `created_by` của kỳ chính là thời điểm và người phát hành.

Request body:

```json
{
  "year": 2026,
  "month": 12,
  "dueDate": "2026-12-10"
}
```

Response 201:

```json
{
  "periodId": "per_202612",
  "year": 2026,
  "month": 12,
  "dueDate": "2026-12-10",
  "issuedInvoiceCount": 172,
  "skippedVacantCount": 28,
  "totalAmount": 593400000,
  "issuedAt": "2026-11-28T09:00:00+07:00",
  "issuedBy": "Trần Thị Bình"
}
```

Lỗi: 409 PERIOD\_ALREADY\_EXISTS, 422 INVALID\_DUE\_DATE (dueDate nhỏ hơn hôm nay), 422 TARIFF\_NOT\_CONFIGURED (thiếu phí quản lý, hoặc có căn không rơi vào mức dịch vụ nào).

Việc service làm cho mỗi căn không VACANT và có chủ hộ đang cư trú:

- Ghi bản chụp vào `invoices`: `recipient_resident_id`, `recipient_name` (chủ hộ), `area_m2`, `service_tier_name`, `due_date` bằng dueDate, `status` PENDING.
- Tạo `invoice_items`: MANAGEMENT (số lượng 1), SERVICE (số lượng 1, chọn mức theo `min_area_m2 <= area < max_area_m2`), PARKING\_MOTORBIKE và PARKING\_CAR (số lượng là số xe ACTIVE lúc phát hành, bỏ dòng nếu bằng 0).
- `amount = quantity * unitAmount`, `total_amount` là tổng các dòng. Mỗi dòng lưu `tariff_id`.

## 9. InvoiceController

Dùng chung dạng `InvoiceDetail` (mục 9.3) cho mọi response trả về một hóa đơn.

### 9.1 GET /manager/invoices

Màn hình: **Hóa đơn – Danh sách** (bảng, ô lọc tháng, tòa, trạng thái, ô tìm kiếm). **Căn hộ – Hóa đơn** (tab hóa đơn của căn, gọi kèm `apartmentId`).

Query: `periodId` hoặc `year` + `month`, `towerId`, `apartmentId`, `status` (PENDING | PAID | VOID | OVERDUE), `q` (mã căn, tên chủ hộ), `page`, `size`. Đọc từ view `v_invoices`.

Response 200:

```json
{
  "items": [
    {
      "id": "inv_01",
      "period": {
        "id": "per_202611",
        "year": 2026,
        "month": 11
      },
      "apartment": {
        "id": "apt_01",
        "code": "A-1205",
        "towerCode": "A"
      },
      "recipientName": "Nguyễn Văn An",
      "totalAmount": 3450000,
      "dueDate": "2026-12-10",
      "status": "PENDING",
      "displayStatus": "PENDING",
      "paidOn": null
    }
  ],
  "total": 190,
  "page": 0,
  "size": 20
}
```

Quy tắc:

- Cột Chủ hộ hiển thị `recipientName` (bản chụp lúc phát hành). Cột Trạng thái hiển thị `displayStatus`.
- Nút Ghi nhận thanh toán trên dòng chỉ hiện khi `status` là PENDING (gồm cả dòng quá hạn).
- Lọc `status=OVERDUE` nghĩa là `status = PENDING` và `dueDate` nhỏ hơn hôm nay.

### 9.2 GET /manager/invoices/summary

Màn hình: **Hóa đơn – Danh sách**, bốn ô đếm ở đầu danh sách (Tổng hóa đơn, Đã thanh toán, Chờ thanh toán, Quá hạn).

Query: `periodId` hoặc `year` + `month`, `towerId` (cùng bộ lọc với danh sách, không có `status`, `q`).

Response 200:

```json
{
  "total": 190,
  "paid": 146,
  "pending": 30,
  "overdue": 14,
  "void": 2
}
```

Quy tắc: `total` = `paid` + `pending` + `overdue`, không tính hóa đơn VOID (khớp mockup 146 + 30 + 14 = 190). `pending` là PENDING chưa quá hạn, `overdue` là PENDING quá hạn, cùng định nghĩa với Tổng quan. `void` chỉ để tham khảo, mockup không hiển thị.

### 9.3 GET /manager/invoices/{id}

Màn hình: **Hóa đơn – Chi tiết và ghi nhận thanh toán** (hóa đơn PENDING), **Hóa đơn – Chi tiết (đã thanh toán)** (hóa đơn PAID), **Hóa đơn – Hủy** (lấy tiêu đề "Hóa đơn tháng 11 · A-1205").

Response 200:

```json
{
  "id": "inv_01",
  "period": {
    "id": "per_202611",
    "year": 2026,
    "month": 11
  },
  "apartment": {
    "id": "apt_01",
    "code": "A-1205",
    "towerCode": "A",
    "towerName": "Tòa A"
  },
  "recipient": {
    "residentId": "res_01",
    "fullName": "Nguyễn Văn An"
  },
  "areaM2": 72.5,
  "serviceTierName": "Mức 2",
  "status": "PAID",
  "displayStatus": "PAID",
  "dueDate": "2026-12-10",
  "issuedAt": "2026-11-01T09:00:00+07:00",
  "items": [
    {
      "feeType": "MANAGEMENT",
      "description": "Phí quản lý",
      "quantity": 1,
      "unitAmount": 1200000,
      "amount": 1200000
    },
    {
      "feeType": "SERVICE",
      "description": "Mức 2 (căn 72.5 m²)",
      "quantity": 1,
      "unitAmount": 2050000,
      "amount": 2050000
    },
    {
      "feeType": "PARKING_MOTORBIKE",
      "description": "2 xe máy",
      "quantity": 2,
      "unitAmount": 100000,
      "amount": 200000
    }
  ],
  "totalAmount": 3450000,
  "payment": {
    "paidOn": "2026-11-26",
    "receivedBy": "Trần Văn Q",
    "note": "Nộp tại văn phòng Ban quản lý"
  },
  "void": null
}
```

Quy tắc:

- `payment` chỉ có khi `status` là PAID, các trường hợp khác là null. Khối "Thông tin thanh toán" ở màn đã thanh toán dùng `payment`.
- `void` chỉ có khi `status` là VOID, dạng `{ "reason": "WRONG_DATA", "note": null, "voidedAt": "...", "voidedBy": "Trần Văn Q" }`, còn lại là null.
- Mockup gộp phí gửi xe thành một dòng ("2 xe máy, 1 ô tô"). Backend trả tách theo `feeType` (PARKING\_MOTORBIKE, PARKING\_CAR), frontend gộp hiển thị nếu muốn.
- Số tiền thu trên form thanh toán là `totalAmount`, chỉ đọc.
- Lỗi: 404 INVOICE\_NOT\_FOUND.

### 9.4 POST /manager/invoices/{id}/pay

Màn hình: **Hóa đơn – Chi tiết và ghi nhận thanh toán** (nút Ghi nhận và in biên lai). Cũng gọi được từ dòng "Ghi nhận thanh toán" ở **Hóa đơn – Danh sách**. Sau khi thành công frontend gọi tiếp 9.6 với `type=receipt` để in biên lai.

Request body:

```json
{
  "paidOn": "2026-11-26",
  "note": "Nộp tại văn phòng Ban quản lý"
}
```

Response 200: dạng `InvoiceDetail` (9.3) với `status` PAID và `payment` có giá trị.

Quy tắc:

- Bắt buộc `paidOn` (không ở tương lai, không nhỏ hơn ngày phát hành). `note` không bắt buộc.
- Không nhận số tiền (cư dân nộp đủ cả hóa đơn). `received_by` lấy từ JWT.
- Lỗi: 404 INVOICE\_NOT\_FOUND, 409 INVOICE\_NOT\_PENDING, 422 INVALID\_PAID\_DATE.

### 9.5 POST /manager/invoices/{id}/void

Màn hình: **Hóa đơn – Hủy** (hộp thoại Hủy hóa đơn, mở từ nút Hủy hóa đơn ở màn chi tiết).

Request body:

```json
{
  "reason": "WRONG_DATA",
  "note": "Sai số xe máy"
}
```

Response 200: dạng `InvoiceDetail` (9.3) với `status` VOID và `void` có giá trị.

Quy tắc:

- Bắt buộc `reason` (WRONG\_DATA | WRONG\_APARTMENT | OTHER). `note` không bắt buộc, bắt buộc khi `reason` là OTHER.
- Chỉ hủy khi PENDING. Sau khi hủy, hóa đơn không còn tính vào công nợ và có thể phát hành lại cho căn đó trong cùng kỳ.
- Lỗi: 404 INVOICE\_NOT\_FOUND, 409 INVOICE\_NOT\_PENDING, 422 VOID\_NOTE\_REQUIRED.

### 9.6 GET /manager/invoices/{id}/print?type=invoice|receipt

Màn hình: **Hóa đơn – Chi tiết và ghi nhận thanh toán** (nút In hóa đơn), **Hóa đơn – Chi tiết (đã thanh toán)** (nút In hóa đơn, In biên lai).

Response 200: file PDF (`Content-Type: application/pdf`, `Content-Disposition: inline; filename="hoa-don-2026-11-A-1205.pdf"`). Không có JSON.

Quy tắc: `type=receipt` chỉ khi PAID. Lỗi: 404 INVOICE\_NOT\_FOUND, 422 RECEIPT\_NOT\_AVAILABLE (receipt mà chưa PAID), 422 INVOICE\_VOID (hóa đơn đã hủy).

### 9.7 GET /manager/invoices/export

Màn hình: **Hóa đơn – Danh sách** (nút Xuất danh sách).

Query: cùng bộ lọc với 9.1 (`periodId` hoặc `year` + `month`, `towerId`, `apartmentId`, `status`, `q`), thêm `format` (xlsx | csv, mặc định xlsx). Không phân trang, xuất toàn bộ kết quả lọc.

Response 200: file (`Content-Disposition: attachment`). Cột đề xuất: Căn hộ, Tòa, Chủ hộ, Kỳ, Số tiền, Hạn thanh toán, Trạng thái, Ngày thu.

## 10. IncidentController (BQL)

Dùng chung dạng `IncidentDetail` (mục 10.3) cho mọi response trả về một sự cố.

### 10.1 GET /manager/incidents

Màn hình: **Sự cố – Danh sách**. **Căn hộ – Sự cố** (tab sự cố của căn, gọi kèm `apartmentId`).

Query: `q` (tiêu đề, khu vực, tên người báo), `towerId`, `status` (NEW | ACKNOWLEDGED | IN\_PROGRESS | CLOSED | CANCELLED), `apartmentId`, `page`, `size`. Sắp xếp `createdAt` giảm dần.

Response 200:

```json
{
  "items": [
    {
      "id": "inc_01",
      "title": "Đèn hành lang tầng 12 hỏng",
      "area": "Hành lang tầng 12",
      "tower": {
        "id": "twr_a",
        "code": "A",
        "name": "Tòa A"
      },
      "reporterName": "Nguyễn Thị E",
      "reporterApartmentCode": "A-1205",
      "channel": "PHONE",
      "assigneeName": "Lê Minh T",
      "status": "IN_PROGRESS",
      "cancelReason": null,
      "createdAt": "2026-11-24T08:10:00+07:00"
    }
  ],
  "total": 15,
  "page": 0,
  "size": 20
}
```

Quy tắc:

- Cột Yêu cầu: `title`. Cột Khu vực: "`tower.name` · `area`". Cột Người báo: "`reporterName` (`reporterApartmentCode`)". Cột Ngày gửi: `createdAt`.
- `channel` khác PORTAL thì hiện nhãn "BQL nhập hộ".
- `assigneeName` null thì hiện "Chưa phân công".
- `cancelReason` có giá trị khi CANCELLED, để hiện nhãn "Đã hủy · trùng yêu cầu".

### 10.2 GET /manager/incidents/summary

Màn hình: **Sự cố – Danh sách**, các ô đếm trạng thái ở đầu danh sách.

Query: `towerId` (không bắt buộc).

Response 200:

```json
{
  "total": 15,
  "new": 3,
  "acknowledged": 2,
  "inProgress": 4,
  "closed": 5,
  "cancelled": 1
}
```

### 10.3 GET /manager/incidents/{id}

Màn hình: **Sự cố – Chi tiết** ở cả năm trạng thái: Mới (có nút Tiếp nhận yêu cầu), Đã tiếp nhận (nhập người phụ trách, nút Bắt đầu xử lý), Đang xử lý (ghi chú kết quả, Hoàn thành, Quay lại Đã tiếp nhận), Đã đóng (chỉ xem), Đã hủy (chỉ xem, có khối Thông tin hủy). Khối Hủy yêu cầu hiện ở ba trạng thái đầu.

Response 200:

```json
{
  "id": "inc_01",
  "title": "Đèn hành lang tầng 12 hỏng",
  "status": "IN_PROGRESS",
  "area": "Hành lang tầng 12",
  "tower": {
    "id": "twr_a",
    "code": "A",
    "name": "Tòa A"
  },
  "apartment": {
    "id": "apt_01",
    "code": "A-1205"
  },
  "reporter": {
    "residentId": "res_02",
    "fullName": "Nguyễn Thị E",
    "relation": "SPOUSE"
  },
  "channel": "PHONE",
  "assigneeName": "Lê Minh T",
  "description": "Đèn hành lang gần căn A-1201 không sáng từ vài ngày nay.",
  "attachments": [
    {
      "id": "att_01",
      "fileName": "hanh-lang.jpg",
      "url": "https://minio.example.com/emerald/incidents/2026/11/5d0c1e7a-3b4f-4c1e-9a52-0f6d8e21b7aa.jpg"
    }
  ],
  "timeline": [
    {
      "status": "NEW",
      "at": "2026-11-24T08:10:00+07:00",
      "actorName": null,
      "assigneeName": null,
      "note": null
    },
    {
      "status": "ACKNOWLEDGED",
      "at": "2026-11-24T09:00:00+07:00",
      "actorName": "Trần Văn Q",
      "assigneeName": null,
      "note": null
    },
    {
      "status": "IN_PROGRESS",
      "at": "2026-11-24T10:30:00+07:00",
      "actorName": "Trần Văn Q",
      "assigneeName": "Lê Minh T",
      "note": null
    }
  ],
  "resultNote": null,
  "cancellation": null,
  "allowedActions": [
    "COMPLETE",
    "REVERT",
    "CHANGE_ASSIGNEE",
    "CANCEL"
  ],
  "createdAt": "2026-11-24T08:10:00+07:00"
}
```

Quy tắc:

- `reporter` là null khi BQL nhập hộ với người báo chọn "Khác (nhập tên)". Khi đó dùng `reporterName`. Để thống nhất, response luôn có thêm `reporterName` (tên hiển thị) và `reporterApartmentCode` giống 10.1, ví dụ trên rút gọn.
- Dòng "Đã gửi · 24/11 08:10" ở Tiến độ là event có `status` NEW. Các bước chưa tới (Đã tiếp nhận, Đang xử lý, Đã đóng) frontend tự vẽ mờ theo trạng thái hiện tại.
- Ảnh: frontend dùng `attachments[].url` để hiển thị trực tiếp, không cần gọi thêm endpoint nào.
- `resultNote` có giá trị khi CLOSED.
- `cancellation` có khi CANCELLED: `{ "reason": "DUPLICATE", "note": null, "cancelledBy": "MANAGER", "cancelledByName": "Trần Văn Q", "cancelledAt": "2026-11-21T10:00:00+07:00" }`. `cancelledBy` là MANAGER hoặc RESIDENT. Màn Đã hủy hiện "Lý do hủy" và "Hủy bởi: Ban quản lý".
- `allowedActions` gồm các giá trị ACKNOWLEDGE, START, CHANGE\_ASSIGNEE, COMPLETE, REVERT, CANCEL, tính từ bảng chuyển trạng thái. Danh sách rỗng khi CLOSED hoặc CANCELLED.
- Lỗi: 404 INCIDENT\_NOT\_FOUND.

### 10.4 POST /manager/incidents

Màn hình: **Sự cố – BQL tạo thay cư dân** (nút Tạo yêu cầu). Các ô chọn lấy dữ liệu từ `GET /towers`, `GET /manager/apartments` và `GET /manager/apartments/{id}/residencies?current=true` (danh sách Người báo gồm cả chủ hộ lẫn thành viên). Ảnh gửi kèm ngay trong request này, không có bước tải ảnh riêng.

Request: `Content-Type: multipart/form-data`. Các phần (part):

| Part | Kiểu | Bắt buộc | Ví dụ |
|---|---|---|---|
| `apartmentId` | text | Có | `apt_01` |
| `reportedBy` | text | Một trong hai với `reporterName` | `res_02` |
| `reporterName` | text | Một trong hai với `reportedBy` | `Nguyễn Văn Khách` |
| `channel` | text | Có | `PHONE` |
| `towerId` | text | Không | `twr_a` |
| `area` | text | Có | `Hành lang tầng 12` |
| `title` | text | Có | `Đèn hành lang tầng 12 hỏng` |
| `description` | text | Không | `Đèn hành lang gần căn A-1201 không sáng từ vài ngày nay.` |
| `files` | file, gửi lặp nhiều lần | Không | `hanh-lang.jpg` |

Response 201: dạng `IncidentDetail` (10.3) với `status` ACKNOWLEDGED, `timeline` có hai dòng (NEW rồi ACKNOWLEDGED, cùng thời điểm), `attachments` có `url` của từng ảnh đã lưu.

Quy tắc:

- `channel` là PHONE | IN\_PERSON | OTHER. `reportedBy` phải là người đang cư trú tại căn. `reporterName` dùng khi chọn "Khác (nhập tên)". `towerId` mặc định là tòa của căn (xem mục 14, điểm 1).
- Ảnh: chỉ nhận image/jpeg, image/png, image/webp, tối đa 5 ảnh mỗi yêu cầu, mỗi ảnh tối đa 5 MB (đề xuất). Backend kiểm tra loại file thật bằng nội dung file, không chỉ dựa vào `Content-Type` hay đuôi file.
- Backend lưu ảnh lên MinIO với tên đối tượng ngẫu nhiên dạng `incidents/{yyyy}/{MM}/{uuid}.{ext}`, rồi lưu URL vào cột `storage_key` của `incident_attachments`. Nếu giao dịch DB lỗi thì backend xóa các ảnh vừa tải lên.
- Lỗi: 404 APARTMENT\_NOT\_FOUND, 422 REPORTER\_REQUIRED, 422 REPORTER\_NOT\_IN\_APARTMENT, 422 FILE\_TYPE\_NOT\_ALLOWED, 422 FILE\_TOO\_LARGE, 422 TOO\_MANY\_FILES, 502 STORAGE\_ERROR.

### 10.5 POST /manager/incidents/{id}/acknowledge

Màn hình: **Sự cố – Chi tiết (Mới)**, nút Tiếp nhận yêu cầu. Không có body.

Response 200: dạng `IncidentDetail`, `status` ACKNOWLEDGED. Lỗi: 404 INCIDENT\_NOT\_FOUND, 409 INVALID\_STATUS\_TRANSITION.

### 10.6 POST /manager/incidents/{id}/start

Màn hình: **Sự cố – Chi tiết (Đã tiếp nhận)**, ô Người phụ trách và nút Bắt đầu xử lý.

Request body:

```json
{
  "assigneeName": "Lê Minh T"
}
```

Response 200: dạng `IncidentDetail`, `status` IN\_PROGRESS. Lỗi: 404 INCIDENT\_NOT\_FOUND, 409 INVALID\_STATUS\_TRANSITION, 422 ASSIGNEE\_REQUIRED.

### 10.7 PATCH /manager/incidents/{id}/assignee

Màn hình: **Sự cố – Chi tiết (Đang xử lý)**, sửa Người phụ trách.

Request body:

```json
{
  "assigneeName": "Phạm Quang H"
}
```

Response 200: dạng `IncidentDetail`. Chỉ khi IN\_PROGRESS, không thì 409 INVALID\_STATUS\_TRANSITION. Lỗi: 422 ASSIGNEE\_REQUIRED.

### 10.8 POST /manager/incidents/{id}/complete

Màn hình: **Sự cố – Chi tiết (Đang xử lý)**, ô Ghi chú kết quả xử lý và nút Hoàn thành.

Request body:

```json
{
  "note": "Đã thay bóng đèn mới."
}
```

Response 200: dạng `IncidentDetail`, `status` CLOSED, `resultNote` có giá trị. Cư dân thấy `resultNote` ở màn chi tiết của họ. Lỗi: 409 INVALID\_STATUS\_TRANSITION, 422 COMPLETE\_NOTE\_REQUIRED.

### 10.9 POST /manager/incidents/{id}/revert

Màn hình: **Sự cố – Chi tiết (Đang xử lý)**, nút Quay lại Đã tiếp nhận. Không có body.

Response 200: dạng `IncidentDetail`, `status` ACKNOWLEDGED. Lỗi: 409 INVALID\_STATUS\_TRANSITION.

### 10.10 POST /manager/incidents/{id}/cancel

Màn hình: **Sự cố – Chi tiết** ở trạng thái Mới, Đã tiếp nhận, Đang xử lý (khối Hủy yêu cầu).

Request body:

```json
{
  "cancelReason": "DUPLICATE",
  "note": "Trùng với yêu cầu ngày 19/11"
}
```

Response 200: dạng `IncidentDetail`, `status` CANCELLED, `cancellation` có giá trị.

Quy tắc: `cancelReason` bắt buộc (MISTAKE | DUPLICATE | OUT\_OF\_SCOPE | OTHER), `note` bắt buộc khi OTHER. Lỗi: 409 INVALID\_STATUS\_TRANSITION (đã CLOSED hoặc CANCELLED), 422 CANCEL\_NOTE\_REQUIRED.

Mỗi lần chuyển trạng thái (10.4 đến 10.10, trừ 10.7) ghi thêm một dòng `incident_events` gồm trạng thái mới, người phụ trách, ghi chú, người thực hiện (từ JWT).

## 11. ResidentPortalController (cổng cư dân)

Tất cả endpoint dưới đây do chủ hộ gọi, tự giới hạn theo căn của người đăng nhập (mục 2). Không có endpoint trả dữ liệu căn khác: truy cập hóa đơn hoặc sự cố không thuộc căn mình trả 404.

### 11.1 GET /resident/home

Màn hình: **Cổng cư dân – Trang chủ** (lời chào, thẻ hóa đơn tháng, thẻ Yêu cầu sự cố, bảng Lịch sử hóa đơn).

Response 200:

```json
{
  "resident": {
    "fullName": "Nguyễn Văn An"
  },
  "apartment": {
    "id": "apt_01",
    "code": "A-1205",
    "towerName": "Tòa A"
  },
  "currentInvoice": {
    "id": "inv_01",
    "period": {
      "year": 2026,
      "month": 11
    },
    "totalAmount": 3450000,
    "dueDate": "2026-12-10",
    "displayStatus": "PENDING"
  },
  "openIncidents": [
    {
      "id": "inc_01",
      "title": "Đèn hành lang tầng 12 hỏng",
      "status": "IN_PROGRESS"
    }
  ],
  "recentInvoices": [
    {
      "id": "inv_01",
      "period": {
        "year": 2026,
        "month": 11
      },
      "totalAmount": 3450000,
      "dueDate": "2026-12-10",
      "displayStatus": "PENDING"
    }
  ]
}
```

Quy tắc:

- `currentInvoice` là hóa đơn của kỳ phát hành gần nhất dành cho căn, không tính VOID, null nếu chưa có. Thẻ hiện "Chờ thanh toán" hoặc "Đã thanh toán" theo `displayStatus`.
- `openIncidents` là sự cố của căn ở trạng thái NEW, ACKNOWLEDGED, IN\_PROGRESS, mới nhất trước, tối đa 5.
- `recentInvoices` là ba kỳ gần nhất (gồm kỳ hiện tại), không tính VOID.

### 11.2 GET /resident/profile

Màn hình: **Cư dân – Hồ sơ của tôi** (chỉ đọc; nút Đổi mật khẩu dùng `POST /me/change-password`, nút Thêm thành viên mở màn Thêm thành viên).

Response 200:

```json
{
  "personal": {
    "fullName": "Nguyễn Văn An",
    "birthDate": "1985-09-20",
    "phone": "0901234567",
    "email": "an.nguyen@example.com",
    "nationalIdMasked": "012*****789"
  },
  "account": {
    "username": "0901234567"
  },
  "apartment": {
    "id": "apt_01",
    "code": "A-1205",
    "towerName": "Tòa A",
    "floor": 12,
    "areaM2": 72.5,
    "bedrooms": 2,
    "bathrooms": 2,
    "residingSince": "2024-03-01"
  },
  "housemates": [
    {
      "residentId": "res_02",
      "fullName": "Nguyễn Thị Hoa",
      "relation": "SPOUSE",
      "phone": "0912000456"
    }
  ],
  "vehicles": [
    {
      "id": "veh_01",
      "type": "MOTORBIKE",
      "plateNumber": "29B1-123.45",
      "cardNumber": "MB-000123"
    }
  ]
}
```

Quy tắc: `housemates` là người cùng ở, không gồm chính chủ hộ. `vehicles` chỉ gồm xe ACTIVE của cả căn. Thông tin cá nhân chỉ đọc (muốn đổi phải liên hệ BQL).

### 11.3 POST /resident/household-members

Màn hình: **Cư dân – Thêm thành viên**.

Request body:

```json
{
  "fullName": "Nguyễn Minh Khôi",
  "relation": "CHILD",
  "birthDate": "2015-06-01",
  "phone": null,
  "nationalId": null
}
```

Response 201:

```json
{
  "residentId": "res_05",
  "residencyId": "rsd_05",
  "fullName": "Nguyễn Minh Khôi",
  "relation": "CHILD",
  "startDate": "2026-11-28"
}
```

Quy tắc:

- Bắt buộc `fullName`, `relation` (SPOUSE | CHILD | PARENT | SIBLING | OTHER). Còn lại không bắt buộc.
- Có hiệu lực ngay, không cần BQL duyệt. Tạo hồ sơ cư dân và cư trú MEMBER với `startDate` là hôm nay, không tạo tài khoản.
- Lỗi: 400 VALIDATION\_ERROR, 403 NO\_ACTIVE\_RESIDENCY, 409 RESIDENT\_ALREADY\_RESIDING (CCCD trùng với người đang cư trú ở căn khác).

### 11.4 GET /resident/invoices

Màn hình: **Cư dân – Hóa đơn** (thẻ Cần thanh toán và bảng hóa đơn).

Query: `page`, `size`. Không có hóa đơn VOID. Sắp xếp kỳ giảm dần.

Response 200:

```json
{
  "outstanding": {
    "invoiceCount": 1,
    "totalAmount": 3450000,
    "earliestDueDate": "2026-12-10",
    "invoiceId": "inv_01"
  },
  "items": [
    {
      "id": "inv_01",
      "period": {
        "year": 2026,
        "month": 11
      },
      "totalAmount": 3450000,
      "dueDate": "2026-12-10",
      "displayStatus": "PENDING",
      "paidOn": null
    }
  ],
  "total": 12,
  "page": 0,
  "size": 20
}
```

Quy tắc: `outstanding` là tổng các hóa đơn PENDING (gồm quá hạn) của căn, null khi không còn khoản nào. Thẻ "Cần thanh toán · hóa đơn tháng 11" lấy kỳ từ hóa đơn `invoiceId`. Cư dân không tự thanh toán trên hệ thống (nộp trực tiếp tại BQL).

### 11.5 GET /resident/invoices/{id} (đề xuất thêm)

Màn hình: nút **Xem chi tiết** ở **Cổng cư dân – Trang chủ** và **Cư dân – Hóa đơn**. Mockup có nút nhưng tài liệu controller chưa có endpoint (xem mục 14, điểm 4).

Response 200: cùng dạng `InvoiceDetail` (9.3), bỏ `recipient`, `payment.receivedBy` và `void`.

Lỗi: 404 INVOICE\_NOT\_FOUND (không thuộc căn mình hoặc đã VOID).

### 11.6 GET /resident/incidents

Màn hình: **Cư dân – Yêu cầu sự cố (sau khi gửi)** (danh sách và thông báo "Đã gửi yêu cầu" hiện sau khi gửi xong).

Query: `status` (không bắt buộc), `page`, `size`. Sắp xếp `createdAt` giảm dần.

Response 200:

```json
{
  "items": [
    {
      "id": "inc_07",
      "title": "Đèn sảnh tòa A không sáng",
      "status": "NEW",
      "assigneeName": null,
      "cancelReason": null,
      "createdAt": "2026-11-27T09:15:00+07:00"
    }
  ],
  "total": 5,
  "page": 0,
  "size": 20
}
```

Quy tắc: `assigneeName` null thì hiện "Chưa phân công".

### 11.7 GET /resident/incidents/{id}

Màn hình: **Cư dân – Chi tiết yêu cầu (Mới, có Hủy)** và **Cư dân – Chi tiết yêu cầu (Đã đóng)** (khối Kết quả xử lý của Ban quản lý, nút Gửi yêu cầu mới).

Response 200:

```json
{
  "id": "inc_08",
  "title": "Đèn cầu thang bộ tầng 8 hỏng",
  "status": "CLOSED",
  "area": "Cầu thang bộ tầng 8",
  "assigneeName": "Lê Minh T",
  "description": "Đèn cầu thang bộ tầng 8 không sáng.",
  "attachments": [
    {
      "id": "att_02",
      "fileName": "cau-thang.jpg",
      "url": "https://minio.example.com/emerald/incidents/2026/11/9a1b2c3d-5e6f-4a7b-8c9d-1e2f3a4b5c6d.jpg"
    }
  ],
  "timeline": [
    {
      "status": "NEW",
      "at": "2026-11-22T08:00:00+07:00",
      "assigneeName": null
    },
    {
      "status": "ACKNOWLEDGED",
      "at": "2026-11-22T09:00:00+07:00",
      "assigneeName": null
    },
    {
      "status": "IN_PROGRESS",
      "at": "2026-11-23T10:00:00+07:00",
      "assigneeName": "Lê Minh T"
    },
    {
      "status": "CLOSED",
      "at": "2026-11-24T15:00:00+07:00",
      "assigneeName": "Lê Minh T"
    }
  ],
  "resultNote": "Đã thay bóng đèn mới.",
  "cancellation": null,
  "canCancel": false,
  "createdAt": "2026-11-22T08:00:00+07:00"
}
```

Quy tắc:

- Cư dân không thấy tên nhân viên BQL thực hiện (`actorName`), chỉ thấy người phụ trách.
- `canCancel` là true khi `status` là NEW (hiện khối Hủy yêu cầu, ghi chú "Chỉ hủy được khi Ban quản lý chưa tiếp nhận").
- `cancellation` có khi CANCELLED, dạng `{ "reason": "DUPLICATE", "cancelledBy": "MANAGER" }`. Cư dân thấy lý do hủy.
- Lỗi: 404 INCIDENT\_NOT\_FOUND (không thuộc căn mình).

### 11.8 POST /resident/incidents

Màn hình: **Cư dân – Gửi yêu cầu** (nút Gửi yêu cầu). Ảnh gửi kèm ngay trong request này. Sau khi thành công, frontend chuyển về danh sách (11.6) và hiện thông báo.

Request: `Content-Type: multipart/form-data`. Các phần (part):

| Part | Kiểu | Bắt buộc | Ví dụ |
|---|---|---|---|
| `title` | text | Có | `Đèn sảnh tòa A không sáng` |
| `area` | text | Có | `Sảnh tòa A` |
| `description` | text | Có | `Đèn sảnh tầng trệt tắt từ tối qua.` |
| `files` | file, gửi lặp nhiều lần | Không | `den-sanh.jpg` |

Response 201: dạng chi tiết ở 11.7 với `status` NEW, `canCancel` true, `timeline` có một dòng NEW, `attachments` có `url` của từng ảnh đã lưu.

Quy tắc: `title`, `area`, `description` bắt buộc (mockup đánh dấu \*). Server đặt `channel` = PORTAL, người báo là chủ hộ đang đăng nhập, căn lấy từ JWT. Quy tắc về ảnh và cách lưu giống 10.4. Lỗi: 400 VALIDATION\_ERROR, 422 FILE\_TYPE\_NOT\_ALLOWED, 422 FILE\_TOO\_LARGE, 422 TOO\_MANY\_FILES, 502 STORAGE\_ERROR.

### 11.9 POST /resident/incidents/{id}/cancel

Màn hình: **Cư dân – Chi tiết yêu cầu (Mới, có Hủy)**, nút Hủy yêu cầu. Không có body.

Response 200: dạng chi tiết ở 11.7 với `status` CANCELLED, `canCancel` false.

Quy tắc: chỉ khi NEW và thuộc căn của mình. Lỗi: 404 INCIDENT\_NOT\_FOUND, 409 INVALID\_STATUS\_TRANSITION (BQL đã tiếp nhận).

## 12. Mã lỗi

| Mã | HTTP | Ngữ cảnh |
|---|---|---|
| VALIDATION\_ERROR | 400 | Sai định dạng hoặc thiếu field bắt buộc (`details` liệt kê field) |
| WRONG\_CURRENT\_PASSWORD, WEAK\_PASSWORD, SAME\_PASSWORD | 422 | Đổi mật khẩu, thêm chủ hộ, đặt lại mật khẩu |
| FILE\_TYPE\_NOT\_ALLOWED, FILE\_TOO\_LARGE, TOO\_MANY\_FILES | 422 | Ảnh khi tạo sự cố (10.4, 11.8) |
| NO\_ACTIVE\_RESIDENCY | 403 | Mọi endpoint `/resident/**` |
| TOWER\_NOT\_FOUND, APARTMENT\_NOT\_FOUND, RESIDENT\_NOT\_FOUND, RESIDENCY\_NOT\_FOUND, VEHICLE\_NOT\_FOUND, INVOICE\_NOT\_FOUND, INCIDENT\_NOT\_FOUND | 404 | Không thấy đối tượng |
| APARTMENT\_CODE\_DUPLICATED | 409 | Thêm căn hộ |
| APARTMENT\_ALREADY\_HAS\_HEAD, USERNAME\_DUPLICATED, RESIDENT\_ALREADY\_RESIDING | 409 | Thêm chủ hộ, chuyển đi, thêm thành viên |
| APARTMENT\_REPAIRING | 422 | Thêm chủ hộ |
| NO\_ACCOUNT | 422 | Đặt lại mật khẩu, khóa tài khoản |
| ACCOUNT\_LOCKED, ACCOUNT\_ALREADY\_LOCKED | 409 | Đặt lại mật khẩu, khóa tài khoản |
| RESIDENCY\_ALREADY\_ENDED | 409 | Chuyển đi |
| INVALID\_END\_DATE, NEW\_HEAD\_REQUIRED, NEW\_HEAD\_INVALID, NEW\_HEAD\_ACCOUNT\_REQUIRED, VEHICLE\_ACTION\_REQUIRED, TRANSFER\_TARGET\_INVALID | 422 | Chuyển đi |
| USER\_NOT\_IN\_APARTMENT, VEHICLE\_QUOTA\_EXCEEDED, ISSUED\_AT\_IN\_FUTURE | 422 | Thêm phương tiện |
| PLATE\_NUMBER\_DUPLICATED, CARD\_NUMBER\_DUPLICATED | 409 | Thêm phương tiện |
| VEHICLE\_ALREADY\_REVOKED | 409 | Thu hồi thẻ |
| INVALID\_REVOKED\_DATE | 422 | Thu hồi thẻ |
| PERIOD\_ALREADY\_EXISTS | 409 | Xem trước và phát hành kỳ |
| TARIFF\_NOT\_CONFIGURED, INVALID\_DUE\_DATE | 422 | Xem trước và phát hành kỳ |
| INVOICE\_NOT\_PENDING | 409 | Ghi nhận thanh toán, hủy hóa đơn |
| INVALID\_PAID\_DATE, VOID\_NOTE\_REQUIRED | 422 | Ghi nhận thanh toán, hủy hóa đơn |
| RECEIPT\_NOT\_AVAILABLE, INVOICE\_VOID | 422 | In hóa đơn, in biên lai |
| INVALID\_STATUS\_TRANSITION | 409 | Mọi hành động chuyển trạng thái sự cố |
| ASSIGNEE\_REQUIRED, COMPLETE\_NOTE\_REQUIRED, CANCEL\_NOTE\_REQUIRED | 422 | Bắt đầu xử lý, đổi người phụ trách, hoàn thành, hủy sự cố |
| REPORTER\_REQUIRED, REPORTER\_NOT\_IN\_APARTMENT | 422 | BQL tạo sự cố |
| IDENTITY\_PROVIDER\_ERROR | 502 | Lỗi khi tạo, khóa hoặc đặt lại mật khẩu trên Keycloak |
| STORAGE\_ERROR | 502 | Lỗi khi lưu ảnh lên MinIO |

## 13. Bảng tra: màn hình và endpoint

| Màn hình (mockup) | Endpoint |
|---|---|
| Đăng nhập | Không thuộc tài liệu này (AuthController, Keycloak) |
| Đổi mật khẩu lần đầu | `GET /me`, `POST /me/change-password` |
| Tổng quan | `GET /manager/dashboard` |
| Căn hộ – Danh sách | `GET /towers`, `GET /manager/apartments` |
| Căn hộ – Tổng quan | `GET /manager/apartments/{id}` |
| Căn hộ – Cư trú | `GET /manager/apartments/{id}/residencies` |
| Căn hộ – Phương tiện | `GET /manager/apartments/{id}/vehicles` |
| Căn hộ – Hóa đơn | `GET /manager/invoices?apartmentId=` |
| Căn hộ – Sự cố | `GET /manager/incidents?apartmentId=` |
| Căn hộ – Thêm | `GET /towers`, `POST /manager/apartments` |
| Phương tiện – Thêm | `GET /manager/apartments/{id}/vehicles` (hạn mức), `GET /manager/apartments/{id}/residencies?current=true` (người dùng xe), `POST /manager/apartments/{apartmentId}/vehicles` |
| Chuyển đi – Thu hồi thẻ xe | `GET /manager/apartments/{id}/vehicles` (frontend lọc xe của người rời đi), `GET /manager/apartments/{id}/residencies?current=true` (người nhận thẻ), `POST /manager/residencies/{residencyId}/move-out` |
| Chuyển đi – Chủ hộ (chọn chủ hộ mới) | `GET /manager/apartments/{id}/residencies?current=true`, `POST /manager/residencies/{residencyId}/move-out` |
| Cư dân – Danh sách (tên file trong mockup: "Quan hệ và hình thức cư trú") | `GET /towers`, `GET /manager/residents` |
| Cư dân – Hồ sơ | `GET /manager/residents/{id}`, `POST /manager/residents/{id}/account/reset-password`, `POST /manager/residents/{id}/account/lock`; nút Chuyển đi dùng `move-out` |
| Cư dân – Thêm chủ hộ | `GET /manager/apartments?hasHead=false`, `POST /manager/residents/heads` |
| Cư dân – Chỉnh sửa hồ sơ | `GET /manager/residents/{id}`, `PUT /manager/residents/{id}` |
| Hóa đơn – Danh sách | `GET /towers`, `GET /manager/invoices`, `GET /manager/invoices/summary`, `GET /manager/invoices/export`, `POST /manager/invoices/{id}/pay` (dòng Ghi nhận thanh toán) |
| Hóa đơn – Tạo hóa đơn tháng | `GET /manager/billing-periods/available-months`, `GET /manager/fee-tariffs/current`, `GET /manager/billing-periods/preview`, `POST /manager/billing-periods` |
| Hóa đơn – Chi tiết và ghi nhận thanh toán | `GET /manager/invoices/{id}`, `POST /manager/invoices/{id}/pay`, `GET /manager/invoices/{id}/print` |
| Hóa đơn – Hủy | `POST /manager/invoices/{id}/void` |
| Hóa đơn – Chi tiết (đã thanh toán) | `GET /manager/invoices/{id}`, `GET /manager/invoices/{id}/print?type=invoice\|receipt` |
| Sự cố – Danh sách | `GET /towers`, `GET /manager/incidents`, `GET /manager/incidents/summary` |
| Sự cố – BQL tạo thay cư dân | `POST /manager/incidents` (kèm ảnh) |
| Sự cố – Chi tiết (Mới, tiếp nhận) | `GET /manager/incidents/{id}`, `POST .../acknowledge`, `POST .../cancel` |
| Sự cố – Chi tiết (Đã tiếp nhận, phân công) | `GET /manager/incidents/{id}`, `POST .../start`, `POST .../cancel` |
| Sự cố – Chi tiết (Đang xử lý, có Hủy) | `GET /manager/incidents/{id}`, `PATCH .../assignee`, `POST .../complete`, `POST .../revert`, `POST .../cancel` |
| Sự cố – Chi tiết (Đã đóng, chỉ xem) | `GET /manager/incidents/{id}` |
| Sự cố – Chi tiết (Đã hủy, chỉ xem) | `GET /manager/incidents/{id}` |
| Cổng cư dân – Trang chủ | `GET /resident/home` |
| Cư dân – Hóa đơn | `GET /resident/invoices`, `GET /resident/invoices/{id}` (đề xuất) |
| Cư dân – Hồ sơ của tôi | `GET /resident/profile`, `POST /me/change-password` |
| Cư dân – Thêm thành viên | `POST /resident/household-members` |
| Cư dân – Yêu cầu sự cố (sau khi gửi) | `GET /resident/incidents` |
| Cư dân – Gửi yêu cầu | `POST /resident/incidents` (kèm ảnh) |
| Cư dân – Chi tiết yêu cầu (Mới, có Hủy) | `GET /resident/incidents/{id}`, `POST /resident/incidents/{id}/cancel` |
| Cư dân – Chi tiết yêu cầu (Đã đóng) | `GET /resident/incidents/{id}` |

Header chung của mọi màn BQL và cư dân (tên người đăng nhập, "Ban quản lý · Trần Văn Q", "Nguyễn Văn A · A-1205") lấy từ `GET /me`.

## 14. Điểm tôi suy ra hoặc đề xuất thêm, cần bạn duyệt

1. **Tòa của sự cố.** Form "BQL tạo thay cư dân" có ô Tòa (A, B, C) riêng với ô căn hộ, và danh sách hiện "Tòa B · Sảnh". Tài liệu controller không có `towerId` trong body, nên tôi thêm `towerId` không bắt buộc (mặc định là tòa của căn). Cần xem bảng `incidents` trong schema có cột tòa chưa. Nếu không, bỏ ô Tòa hoặc lấy tòa từ căn.
2. **Hình thức thanh toán.** Mockup có ô "Hình thức: Tiền mặt" ở form ghi nhận thanh toán, nhưng tài liệu controller chỉ có `paidOn` và `note`. Tôi không đưa `method` vào request. Nếu muốn giữ ô này thì cần thêm cột và field.
3. **Cư dân tự hủy yêu cầu.** Mockup không hỏi lý do khi cư dân hủy, còn BQL hủy phải có `cancelReason`. Tôi để cư dân hủy không có body, `cancellation.cancelledBy` là RESIDENT và `reason` là null. Nếu cột `cancel_reason` bắt buộc thì đặt mặc định MISTAKE.
4. **Chi tiết hóa đơn cho cư dân (11.5).** Hai màn cư dân có nút "Xem chi tiết" hóa đơn nhưng danh sách controller không có endpoint này. Tôi đề xuất thêm `GET /resident/invoices/{id}`.
5. **Đổi người phụ trách (10.7).** Chỉ cập nhật người phụ trách hiện tại, không ghi thêm dòng vào Tiến độ để tránh hai dòng "Đang xử lý". Nếu muốn lưu vết thì cần loại event riêng.
6. **Ghi chú kết quả bắt buộc (10.8).** Tôi cho `note` của Hoàn thành là bắt buộc vì cư dân xem được kết quả xử lý. Nếu muốn không bắt buộc thì bỏ lỗi COMPLETE\_NOTE\_REQUIRED.
7. **Hai dòng Tiến độ khi BQL tạo hộ (10.4).** Sự cố vào thẳng ACKNOWLEDGED nhưng vẫn ghi hai event (NEW rồi ACKNOWLEDGED) để Tiến độ có dòng "Đã gửi".
8. **Vá mục cũ cho khớp mockup.** Mục 8.2 thêm `apartmentStatus` (mockup hiện nhãn "Đang sửa chữa" ở dòng căn). Mục 6.2 thêm `account.createdAt` (ô "Ngày cấp" tài khoản) và `currentResidency.housemates` (dòng "cùng ở: ...").
9. **Dòng trạng thái cũ trong ghi chú của tôi.** Trước đây tôi ghi nhận sự cố có thêm trạng thái chờ cư dân xác nhận. Mockup và tài liệu controller mới (27/11) đã bỏ trạng thái đó, nên bản thiết kế này theo luồng NEW, ACKNOWLEDGED, IN\_PROGRESS, CLOSED hoặc CANCELLED.
10. **Chưa có file schema.** Bạn chưa đính kèm `emerald_schema.sql` nên tên cột (ví dụ cột lưu lý do hủy hóa đơn, `incident_events`) tôi suy từ tài liệu controller. Cần đối chiếu lại khi có file.
11. **Ảnh sự cố lưu qua backend (thay cho 3.4, 3.5 cũ).** Đã bỏ hai endpoint cấp URL tải lên và URL xem có thời hạn. Ảnh gửi cùng request tạo sự cố (multipart), backend lưu lên MinIO và trả `url` để frontend hiển thị trực tiếp. Hệ quả cần biết:
    - Frontend lấy ảnh thẳng từ `url` nên bucket phải cho đọc công khai (hoặc đặt sau một proxy/CDN). Quy tắc cũ "chỉ BQL hoặc cư dân thuộc căn mới xem được ảnh" không còn được kiểm tra ở backend. Tên đối tượng là UUID ngẫu nhiên nên khó đoán, nhưng ai có link thì xem được. Nếu ảnh sự cố cần riêng tư thì phải quay lại URL có thời hạn.
    - Backend cần cấu hình giới hạn multipart đủ cho 5 ảnh mỗi request (tổng khoảng 25 MB).
    - Cột `incident_attachments.storage_key` lưu luôn URL đầy đủ của ảnh, không có tên riêng cho URL. Vì vậy sau này đổi địa chỉ MinIO thì phải cập nhật lại dữ liệu cũ.