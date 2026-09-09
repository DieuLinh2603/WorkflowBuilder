from datetime import date
from docx import Document
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor


OUTPUT = "Dac-ta-chuc-nang-theo-role-WorkflowBuilder-v1.1.docx"


def shade(cell, fill):
    props = cell._tc.get_or_add_tcPr()
    element = OxmlElement("w:shd")
    element.set(qn("w:fill"), fill)
    props.append(element)


def set_cell(cell, value, bold=False, white=False, size=8.5):
    cell.text = ""
    paragraph = cell.paragraphs[0]
    run = paragraph.add_run(str(value))
    run.bold = bold
    run.font.name = "Arial"
    run.font.size = Pt(size)
    if white:
        run.font.color.rgb = RGBColor(255, 255, 255)
    cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER


def table(doc, headers, rows, widths=None, font_size=8.5):
    result = doc.add_table(rows=1, cols=len(headers))
    result.style = "Table Grid"
    result.alignment = WD_TABLE_ALIGNMENT.CENTER
    result.autofit = True
    for i, header in enumerate(headers):
        set_cell(result.rows[0].cells[i], header, bold=True, white=True, size=font_size)
        shade(result.rows[0].cells[i], "1F4E78")
        if widths:
            result.rows[0].cells[i].width = Cm(widths[i])
    for row_no, row in enumerate(rows):
        cells = result.add_row().cells
        for i, value in enumerate(row):
            set_cell(cells[i], value, size=font_size)
            if row_no % 2:
                shade(cells[i], "EAF2F8")
            if widths:
                cells[i].width = Cm(widths[i])
    doc.add_paragraph()
    return result


def bullet(doc, text, level=0):
    p = doc.add_paragraph(style="List Bullet" if level == 0 else "List Bullet 2")
    p.add_run(text)


def numbered(doc, text):
    p = doc.add_paragraph(style="List Number")
    p.add_run(text)


def use_case_table(doc, rows):
    return table(
        doc,
        ["ID", "Chức năng", "Điều kiện/đầu vào", "Luồng xử lý chính", "Kết quả/quy tắc"],
        rows,
        [1.2, 3.2, 4.0, 5.5, 4.0],
        7.7,
    )


doc = Document()
section = doc.sections[0]
section.top_margin = Cm(1.8)
section.bottom_margin = Cm(1.8)
section.left_margin = Cm(1.8)
section.right_margin = Cm(1.8)

styles = doc.styles
normal = styles["Normal"]
normal.font.name = "Arial"
normal._element.rPr.rFonts.set(qn("w:eastAsia"), "Arial")
normal.font.size = Pt(10)
normal.paragraph_format.space_after = Pt(5)
normal.paragraph_format.line_spacing = 1.12
for name, size, color in [
    ("Title", 24, (31, 78, 120)),
    ("Heading 1", 17, (31, 78, 120)),
    ("Heading 2", 13, (46, 116, 181)),
    ("Heading 3", 11, (55, 55, 55)),
]:
    style = styles[name]
    style.font.name = "Arial"
    style._element.rPr.rFonts.set(qn("w:eastAsia"), "Arial")
    style.font.size = Pt(size)
    style.font.bold = True
    style.font.color.rgb = RGBColor(*color)

# Cover
p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_before = Pt(65)
r = p.add_run("ĐẶC TẢ CHỨC NĂNG HỆ THỐNG\nWORKFLOW BUILDER")
r.bold = True
r.font.name = "Arial"
r.font.size = Pt(24)
r.font.color.rgb = RGBColor(31, 78, 120)
p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.add_run("Mô tả hệ thống và chức năng chi tiết theo vai trò").italic = True
doc.add_paragraph()
p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.add_run("Phiên bản tài liệu: 1.1\n")
p.add_run(f"Ngày lập: {date.today().strftime('%d/%m/%Y')}\n")
p.add_run("Trạng thái: Đặc tả theo phiên bản source code hiện tại")
doc.add_page_break()

doc.add_heading("1. Thông tin tài liệu", level=1)
table(doc, ["Thuộc tính", "Nội dung"], [
    ("Tên hệ thống", "Workflow Builder"),
    ("Loại tài liệu", "Software Functional Specification – Đặc tả chức năng"),
    ("Phạm vi", "Web application quản lý workflow nghiệp vụ và data pipeline nội bộ"),
    ("Đối tượng đọc", "Business Analyst, Product Owner, Developer, Tester, System Administrator"),
    ("Nguồn đặc tả", "Source code frontend/backend, API, database migration và automated test hiện có"),
    ("Ngoài phạm vi", "SSO/MFA, multi-tenant, mobile app, BI reporting chuyên sâu và hạ tầng production hoàn chỉnh"),
], [4.2, 12.5])

doc.add_heading("1.1. Mục đích", level=2)
doc.add_paragraph(
    "Tài liệu mô tả phạm vi nghiệp vụ của Workflow Builder, định nghĩa actor, phân quyền và hành vi chi tiết của "
    "từng chức năng. Tài liệu có thể dùng làm baseline để review business, viết test case, lập kế hoạch phát triển "
    "và nghiệm thu hệ thống."
)

doc.add_heading("1.2. Thuật ngữ", level=2)
table(doc, ["Thuật ngữ", "Định nghĩa"], [
    ("Workflow family", "Nhóm các version của cùng một quy trình nghiệp vụ."),
    ("Workflow version", "Một phiên bản definition cụ thể; version đã publish được xem là bất biến."),
    ("Step", "Một nút xử lý trong workflow."),
    ("Connection", "Đường chuyển trạng thái giữa hai step, có thể kèm điều kiện."),
    ("Instance", "Một lần chạy workflow cho một request hoặc một batch."),
    ("Task", "Công việc cần một user xử lý tại Approval, Review hoặc Assignment step."),
    ("Audience", "Tập user/group/system role được phép tạo request từ workflow."),
    ("Activation", "Một vòng kích hoạt của step; dùng phân biệt task mới với task của vòng xử lý trước."),
    ("Pipeline", "Định nghĩa lấy, ghép, biến đổi và xuất dữ liệu."),
    ("Dataset", "Đầu ra có version của pipeline."),
    ("Data Binding", "Cấu hình ánh xạ Dataset record vào Start form để tạo batch workflow."),
], [4.0, 12.7])

doc.add_heading("2. Mô tả tổng quan hệ thống", level=1)
doc.add_paragraph(
    "Workflow Builder là ứng dụng web nội bộ hỗ trợ thiết kế và vận hành quy trình phê duyệt/xử lý mà không phải "
    "viết riêng từng luồng nghiệp vụ. Hệ thống gồm hai phân hệ liên kết với nhau: Workflow Management/Runtime và Data Pipeline."
)
doc.add_heading("2.1. Phân hệ Workflow", level=2)
for item in [
    "Quản lý workflow, version và vòng đời publish.",
    "Thiết kế graph trực quan bằng node và connection.",
    "Cấu hình form, người xử lý, deadline, điều kiện và thông báo.",
    "Nhận request đơn hoặc batch.",
    "Sinh task, điều hướng request và ghi lịch sử xử lý.",
]:
    bullet(doc, item)
doc.add_heading("2.2. Phân hệ Data Pipeline", level=2)
for item in [
    "Quản lý connector REST và PostgreSQL.",
    "Nhận dữ liệu CSV, REST API hoặc câu lệnh PostgreSQL SELECT.",
    "Join, filter, chuẩn hóa, tổng hợp và định nghĩa output schema.",
    "Tạo Dataset version và phát hiện record mới/thay đổi.",
    "Ánh xạ Dataset vào workflow để tự động tạo batch request.",
]:
    bullet(doc, item)

doc.add_heading("2.3. Kiến trúc logic", level=2)
p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run(
    "Người dùng → React Web UI → REST API/JWT → Spring Boot Services\n"
    "→ Workflow Engine / Pipeline Engine / Scheduler → PostgreSQL\n"
    "→ Email / Teams / Webhook / REST source / PostgreSQL source"
)
r.font.name = "Consolas"
r.font.size = Pt(9.5)

doc.add_heading("3. Actor và nguyên tắc phân quyền", level=1)
table(doc, ["Actor", "Mô tả", "Phạm vi chính"], [
    ("Khách chưa đăng nhập", "Người chưa có phiên JWT hợp lệ.", "Login, forgot password, reset password."),
    ("ADMIN", "Quản trị toàn hệ thống.", "User, group, settings, connector, toàn bộ workflow/pipeline/instance."),
    ("WORKFLOW_OWNER", "Chủ sở hữu quy trình.", "Workflow do mình sở hữu, pipeline của mình và data binding liên quan."),
    ("EDITOR", "Người được cộng tác chỉnh workflow.", "Draft workflow được owner gán quyền editor."),
    ("VIEWER", "Người dùng nghiệp vụ thông thường.", "Catalog, request, task và workflow được phép xem/được giao."),
    ("Task actor", "Bất kỳ user active nào được resolve làm assignee.", "Approve, reject, review hoặc complete task được giao."),
], [3.0, 5.5, 8.2])

doc.add_heading("3.1. Nguyên tắc", level=2)
for item in [
    "Một user có thể có nhiều system role; UI lấy role ưu tiên theo thứ tự ADMIN → WORKFLOW_OWNER → EDITOR → VIEWER để chọn dashboard/menu chính.",
    "System role và workflow-scoped permission là hai lớp khác nhau. User mang role VIEWER vẫn có thể là editor của một workflow cụ thể nếu được owner gán.",
    "Workflow chỉ được chỉnh sửa khi ở trạng thái DRAFT và user là Admin, owner hoặc scoped editor.",
    "Chỉ Admin hoặc owner của workflow được publish, tạo version mới, restore, duplicate, suspend, archive và quản lý editor.",
    "Chỉ user thuộc audience của published workflow mới được submit request.",
    "Quyền xử lý task phụ thuộc assignee, không phụ thuộc riêng vào system role.",
    "Frontend route guard chỉ kiểm soát điều hướng; backend/service là nguồn quyết định quyền cuối cùng.",
]:
    bullet(doc, item)

doc.add_heading("4. Ma trận phân quyền", level=1)
permission_rows = [
    ("Đăng nhập/đặt lại mật khẩu", "✓", "✓", "✓", "✓"),
    ("Xem dashboard", "✓", "✓", "✓", "✓"),
    ("Đổi mật khẩu/đăng xuất", "✓", "✓", "✓", "✓"),
    ("Xem notification", "✓", "✓", "✓", "✓"),
    ("Quản lý user", "✓", "—", "—", "—"),
    ("Quản lý group/settings", "✓", "—", "—", "—"),
    ("Quản lý connector", "✓", "Dùng connector được cấp", "Dùng gián tiếp", "—"),
    ("Tạo workflow", "✓", "✓", "—", "—"),
    ("Xem workflow", "Tất cả", "Sở hữu/được cấp/audience", "Được cấp/audience", "Được cấp/audience"),
    ("Sửa workflow draft", "Tất cả draft", "Workflow sở hữu", "Workflow được gán", "Chỉ khi được gán scoped editor"),
    ("Validate workflow draft", "✓", "✓", "Theo quyền edit", "Theo quyền edit"),
    ("Publish/version/restore", "✓", "Workflow sở hữu", "—", "—"),
    ("Quản lý editor", "✓", "Workflow sở hữu", "—", "—"),
    ("Chuyển owner", "✓", "—", "—", "—"),
    ("Tạo/quản lý pipeline", "✓", "Pipeline sở hữu", "—", "—"),
    ("Tạo Data Binding", "✓", "Phải sở hữu pipeline và workflow", "—", "—"),
    ("Xem Catalog/gửi request", "Theo audience", "Theo audience", "Theo audience", "Theo audience"),
    ("Xem request của mình", "✓", "✓", "✓", "✓"),
    ("Xem instance quản lý được", "Tất cả", "Workflow sở hữu", "Workflow được giao", "Theo quyền liên quan"),
    ("Xử lý task được giao", "✓", "✓", "✓", "✓"),
    ("Withdraw request của mình", "✓", "✓", "✓", "✓"),
]
table(doc, ["Chức năng", "ADMIN", "OWNER", "EDITOR", "VIEWER"], permission_rows, [6.3, 2.5, 2.8, 2.8, 2.8], 7.8)

doc.add_heading("5. Chức năng dùng chung", level=1)
common = [
    ("COM-01", "Đăng nhập", "Email, mật khẩu; tài khoản active.", "Gửi thông tin đăng nhập; backend xác thực BCrypt; sinh JWT; UI lưu user và token.", "Thành công chuyển Dashboard; sai thông tin trả lỗi; tài khoản inactive không đăng nhập."),
    ("COM-02", "Quên mật khẩu", "Email người dùng.", "Tạo reset token có thời hạn; gửi email nếu SMTP khả dụng; local có thể trả development link.", "Luôn trả thông báo chung để tránh lộ email có tồn tại hay không."),
    ("COM-03", "Đặt lại mật khẩu", "Reset token hợp lệ và mật khẩu mới.", "Kiểm tra token, expiry, trạng thái đã sử dụng; cập nhật BCrypt password.", "Token dùng một lần; token hết hạn hoặc đã dùng bị từ chối."),
    ("COM-04", "Đổi mật khẩu", "Đã đăng nhập; mật khẩu hiện tại và mật khẩu mới.", "Xác minh mật khẩu hiện tại rồi cập nhật mật khẩu mới.", "Sau khi đổi, UI đăng xuất để yêu cầu đăng nhập lại."),
    ("COM-05", "Xem Dashboard", "JWT hợp lệ.", "UI chọn dashboard theo primary role và tải workflow/instance/task/user count phù hợp.", "Hiển thị thống kê và danh sách gần đây trong phạm vi quyền."),
    ("COM-06", "Notification Center", "JWT hợp lệ.", "Tải danh sách thông báo; hiển thị unread count; mở link liên quan; đánh dấu đã đọc.", "User chỉ nhận/xem notification của chính mình."),
    ("COM-07", "Đăng xuất", "Đang đăng nhập.", "Xóa JWT và user khỏi localStorage; chuyển về Login.", "Phiên phía client kết thúc; hiện chưa có server-side token revoke."),
    ("COM-08", "Xử lý phiên hết hạn", "API trả HTTP 401.", "API wrapper xóa local token/user và phát sự kiện unauthorized.", "UI trở về trạng thái chưa đăng nhập."),
]
use_case_table(doc, common)

doc.add_heading("6. Đặc tả chức năng theo role ADMIN", level=1)
doc.add_paragraph("Admin có phạm vi quản trị rộng nhất và có thể thao tác trên toàn bộ dữ liệu, với điều kiện trạng thái nghiệp vụ vẫn phải hợp lệ.")
admin = [
    ("ADM-01", "Danh sách user", "Admin đăng nhập.", "Tìm kiếm, lọc job title/role, phân trang và mở chi tiết user.", "Chỉ Admin truy cập; trả Page dữ liệu."),
    ("ADM-02", "Tạo user", "Email chưa tồn tại; display name, job title, mật khẩu và role hợp lệ.", "Nhập form; backend validate; mã hóa mật khẩu; lưu user.", "Email duy nhất; mật khẩu tối thiểu theo validation hiện hành."),
    ("ADM-03", "Cập nhật user", "User tồn tại.", "Sửa display name, job title, manager, role và tùy chọn mật khẩu mới.", "Email không đổi trong màn hình edit; manager phải là user hợp lệ."),
    ("ADM-04", "Activate/deactivate user", "User tồn tại.", "Gọi action kích hoạt hoặc vô hiệu hóa.", "User inactive không thể đăng nhập và không được resolve như actor active."),
    ("ADM-05", "Tạo group", "Tên group không rỗng.", "Tạo group ở Settings.", "Group dùng cho audience, assignment hoặc notification; không phải system role."),
    ("ADM-06", "Quản lý thành viên group", "Group và user tồn tại.", "Thêm user chưa thuộc group hoặc xóa member hiện tại.", "Membership ảnh hưởng actor/audience ở lần runtime tiếp theo."),
    ("ADM-07", "Cấu hình reminder", "Lead time từ 1 đến 720 giờ.", "Cập nhật mốc nhắc deadline toàn hệ thống.", "Áp dụng chung cho task có deadline."),
    ("ADM-08", "Cấu hình endpoint reminder", "Endpoint/SMTP được cung cấp qua settings hoặc environment.", "Lưu endpoint Teams/webhook phục vụ worker delivery.", "External delivery phụ thuộc hệ thống ngoài."),
    ("ADM-09", "Quản lý connector", "Tên, type và config hợp lệ.", "Tạo/sửa REST hoặc PostgreSQL connector; nhập credential; bật/tắt; cấp quyền user.", "Credential được mã hóa; API response không trả secret gốc."),
    ("ADM-10", "Kiểm tra connector", "Connector active và config hợp lệ.", "REST thực hiện GET; PostgreSQL mở read-only connection và kiểm tra validity.", "Trả success hoặc chi tiết lỗi kết nối."),
    ("ADM-11", "Xóa connector", "Connector tồn tại.", "Yêu cầu xóa connector.", "Có thể thất bại nếu database constraint còn pipeline tham chiếu."),
    ("ADM-12", "Xem toàn bộ workflow", "Admin đăng nhập.", "Mở màn hình Workflows; hệ thống tải các workflow mà Admin có thể xem, gồm mọi family và version chưa bị loại khỏi truy vấn nghiệp vụ.", "Admin không bị giới hạn bởi owner/editor/audience; có thể mở detail, graph và version history."),
    ("ADM-13", "Tạo workflow", "Tên workflow và metadata hợp lệ.", "Nhập tên, mô tả, loại/module, submission mode và cấu hình khởi tạo; hệ thống tạo workflow family mới.", "Kết quả là DRAFT v1 với một START step mặc định và Admin/owner được chọn làm owner theo request."),
    ("ADM-14", "Chỉnh sửa workflow draft", "Workflow ở trạng thái DRAFT.", "Thêm/sửa/xóa step; thay đổi vị trí; cấu hình form, actor, notification, system action, end step và connection.", "Admin có thể sửa mọi draft. PUBLISHED/SUSPENDED/ARCHIVED không được sửa definition trực tiếp."),
    ("ADM-15", "Validate workflow", "Workflow DRAFT có graph/config.", "Chạy kiểm tra START/END, connection vào-ra, field key, condition, actor config, branch và cycle.", "Trả danh sách toàn bộ lỗi; không thay đổi trạng thái workflow."),
    ("ADM-16", "Publish workflow", "Workflow DRAFT và validation không có lỗi.", "Publish draft; hệ thống tìm version PUBLISHED khác trong cùng family và chuyển chúng sang SUSPENDED.", "Draft trở thành PUBLISHED và nhận request mới. Nếu còn lỗi validation thì từ chối toàn bộ thao tác."),
    ("ADM-17", "Tạo version mới", "Version nguồn đang PUBLISHED hoặc SUSPENDED.", "Sao chép metadata, step, field, connection, audience và editor sang một DRAFT mới trong cùng family.", "Version number tăng; nếu family đã có DRAFT thì trả lại draft hiện có thay vì tạo thêm."),
    ("ADM-18", "So sánh version", "Hai version tồn tại và thuộc phạm vi xem của Admin.", "Chọn version nguồn/đích; hệ thống so definition và trả danh sách thay đổi.", "Chỉ đọc, không sửa version."),
    ("ADM-19", "Restore version", "Version nguồn không phải DRAFT.", "Chọn version lịch sử để kích hoạt lại; các version PUBLISHED khác cùng family bị chuyển SUSPENDED.", "Version được chọn trở thành PUBLISHED. Đây là kích hoạt lại version cũ, không copy thành draft mới theo code hiện tại."),
    ("ADM-20", "Duplicate workflow", "Workflow nguồn tồn tại và Admin có quyền xem.", "Nhập tên/owner đích; sao chép definition sang workflow family mới.", "Tạo workflow DRAFT độc lập; thay đổi bản sao không ảnh hưởng family nguồn."),
    ("ADM-21", "Suspend workflow", "Workflow tồn tại.", "Chọn Suspend; backend cập nhật trạng thái SUSPENDED.", "Workflow không còn là active published version nhận request mới; instance đã tạo vẫn giữ version tham chiếu."),
    ("ADM-22", "Archive workflow", "Workflow tồn tại.", "Chọn Archive; backend cập nhật trạng thái ARCHIVED.", "Giữ dữ liệu/version history nhưng loại khỏi luồng sử dụng thông thường."),
    ("ADM-23", "Xóa workflow draft", "Workflow ở DRAFT và chưa có instance tham chiếu.", "Xác nhận xóa; backend xóa workflow cùng cấu hình phụ thuộc theo quan hệ dữ liệu.", "Chỉ xóa vĩnh viễn DRAFT chưa được sử dụng. PUBLISHED hoặc workflow đã có instance bị từ chối."),
    ("ADM-24", "Bỏ draft không thay đổi", "Draft được sinh từ version khác, definition chưa thay đổi và chưa có instance.", "Gọi discard unchanged draft.", "Draft được xóa; nếu definition đã thay đổi hoặc đã được dùng thì không xóa."),
    ("ADM-25", "Thêm editor", "Workflow DRAFT; user đích active và có role EDITOR hoặc VIEWER.", "Chọn user và thêm vào danh sách scoped editor của workflow.", "Owner không cần được thêm; không thay đổi editor trên version không phải DRAFT."),
    ("ADM-26", "Xóa editor", "Workflow DRAFT; user đang là editor.", "Xóa user khỏi editor list.", "User mất quyền edit draft này; nếu user không thuộc list thì trả Not Found."),
    ("ADM-27", "Chuyển owner", "Workflow và user đích tồn tại; user đích có role WORKFLOW_OWNER.", "Admin chọn owner mới; backend thay quan hệ owner của workflow.", "Chỉ Admin được thực hiện. Code hiện không kiểm tra user đích active và chuyển trên workflow/version được chọn, không tự động chuyển toàn bộ family."),
    ("ADM-28", "Xem toàn bộ pipeline", "Admin đăng nhập.", "Mở Data Pipelines; hệ thống trả mọi pipeline không phân biệt owner; mở designer và run history.", "Admin vượt qua ownership check đối với pipeline."),
    ("ADM-29", "Tạo pipeline", "Tên không rỗng; business key ban đầu hợp lệ.", "Tạo pipeline DRAFT với sources/joins/transforms rỗng, output schema rỗng, lịch MANUAL và timezone Việt Nam.", "Admin trở thành owner của pipeline mới theo current user."),
    ("ADM-30", "Cấu hình nguồn pipeline", "Pipeline tồn tại; connector active/có quyền hoặc file CSV.", "Thêm source alias; chọn REST/PostgreSQL hoặc upload CSV; cấu hình query, recordPath và pagination.", "REST chỉ GET; PostgreSQL chỉ một SELECT; CSV tạo file version và checksum."),
    ("ADM-31", "Cấu hình join/transform", "Đã discover được field hoặc biết schema nguồn.", "Cấu hình INNER/LEFT/FULL join, join key/cardinality; thêm filter, cast, trim, sum, average hoặc chỉnh JSON nâng cao.", "Join key/cardinality và transform được engine validate khi preview/run."),
    ("ADM-32", "Cấu hình output", "Có danh sách field sau source/join/transform.", "Chọn cột nguồn, tên field đầu ra, data type, required và một/nhiều business key.", "Business key dùng nhận diện record giữa các Dataset version."),
    ("ADM-33", "Preview pipeline", "Definition/output schema/business key hợp lệ và nguồn truy cập được.", "Discover source, preview sau join/transform và preview output chính thức.", "Preview chính thức thành công chuyển pipeline sang PREVIEWED; lỗi trả đúng stage SOURCE/JOIN/TRANSFORM/VALIDATION."),
    ("ADM-34", "Publish pipeline", "Pipeline ở PREVIEWED.", "Chọn Kích hoạt; backend đổi status thành PUBLISHED và tính nextRunAt theo schedule.", "Pipeline chưa preview hoặc preview lỗi không được publish."),
    ("ADM-35", "Chạy pipeline thủ công", "Pipeline PUBLISHED.", "Tạo PipelineRun, load nguồn, transform, publish Dataset version và kích hoạt binding.", "Request hiện chạy đồng bộ; kết quả SUCCESS, NO_CHANGES, RETRY hoặc FAILED."),
    ("ADM-36", "Cấu hình lịch pipeline", "Schedule type MANUAL/ONCE/DAILY; thời gian tương ứng hợp lệ.", "Lưu schedule theo Asia/Ho_Chi_Minh và cập nhật nextRunAt nếu pipeline đã publish.", "ONCE cần scheduledAt; DAILY cần dailyTime; chưa hỗ trợ cron/weekly/monthly."),
    ("ADM-37", "Xem run history/Dataset", "Pipeline đã từng chạy hoặc đã sinh Dataset.", "Xem trigger type, input/output/changed count, attempt, error stage/message và Dataset version records.", "Dataset record được phân trang; quyền Admin không bị giới hạn theo owner."),
    ("ADM-38", "Quản lý Data Binding", "Dataset thuộc pipeline truy cập được; workflow đích PUBLISHED.", "Tạo/cập nhật mapping, filter, trigger mode và active flag; có thể chạy binding thủ công.", "Chưa có DELETE endpoint; tắt binding bằng active=false."),
    ("ADM-39", "Xóa pipeline", "Pipeline tồn tại và Admin xác nhận.", "Gọi delete pipeline.", "Có thể bị chặn bởi ràng buộc dữ liệu nếu còn Dataset/Binding phụ thuộc; UI hiện chưa mô tả cascade rõ."),
    ("ADM-40", "Xem toàn bộ instance", "Admin đăng nhập.", "Tải instance, mở detail, history và batch records; theo dõi current step/status.", "Dùng cho giám sát và xử lý sự cố; không tự động cho phép thay assignee."),
    ("ADM-41", "Migration batch record", "Admin vận hành; dữ liệu legacy tồn tại.", "Gọi endpoint migration chuyển batch state cũ sang bảng chuẩn hóa.", "Chức năng kỹ thuật; cần giới hạn vận hành và bổ sung audit khi production."),
]
use_case_table(doc, admin)

doc.add_heading("7. Đặc tả chức năng theo role WORKFLOW_OWNER", level=1)
owner = [
    ("OWN-01", "Tạo workflow", "Owner active; tên và metadata hợp lệ.", "Nhập tên, mô tả, type/module, submission mode và audience ban đầu.", "Tạo DRAFT v1 kèm START step mặc định."),
    ("OWN-02", "Xem workflow quản lý", "Owner đăng nhập.", "Tải các workflow family/version do mình sở hữu hoặc được cấp.", "Dashboard thống kê family, draft, published và instance."),
    ("OWN-03", "Thiết kế graph", "Workflow DRAFT và owner có quyền edit.", "Thêm step, kéo vị trí, nối node, sửa label và xóa step.", "Không cho xóa START; delete step có chiến lược reconnect/disconnect."),
    ("OWN-04", "Cấu hình START", "Có đúng START step.", "Chọn SINGLE/BATCH, audience, max rows, hướng dẫn và dynamic fields.", "BATCH không cho field FILE; audience phải hợp lệ."),
    ("OWN-05", "Cấu hình business step", "Step thuộc draft workflow.", "Cấu hình Approval, Review hoặc Assignment, actor, deadline và policy.", "Actor có thể là fixed user, job title, dynamic field hoặc group tùy step."),
    ("OWN-06", "Cấu hình notification/system action/end", "Step thuộc draft workflow.", "Nhập template, channel, mapping, failure policy hoặc outcome.", "Template/action/outcome bắt buộc theo loại step."),
    ("OWN-07", "Cấu hình connection", "Hai step thuộc cùng draft workflow.", "Chọn connection type, logical operator, priority và condition clauses.", "Type phải tương thích nguồn; IF cần condition; mỗi source tối đa một ELSE."),
    ("OWN-08", "Quản lý editor", "Workflow DRAFT do owner sở hữu.", "Tìm user active và thêm/xóa scoped editor.", "Không thay đổi editor trên published version."),
    ("OWN-09", "Validate workflow", "Draft đã có graph/config.", "Chạy bộ quy tắc validation và xem lỗi.", "Không thay đổi dữ liệu; trả danh sách lỗi theo step/connection."),
    ("OWN-10", "Publish workflow", "Owner workflow; validation không có lỗi.", "Xác nhận publish.", "Version thành PUBLISHED và có thể nhận request; không sửa trực tiếp nữa."),
    ("OWN-11", "Tạo version tiếp theo", "Workflow family có published version.", "Tạo working copy từ version hiện hành.", "Copy definition sang DRAFT mới; instance cũ không bị ảnh hưởng."),
    ("OWN-12", "Compare/restore version", "Có version history.", "Chọn hai version để compare hoặc restore version cũ.", "Restore tạo draft mới, không sửa version lịch sử."),
    ("OWN-13", "Duplicate workflow", "Có quyền xem nguồn và quyền tạo workflow.", "Nhập tên workflow mới và sao chép definition.", "Tạo workflow family mới ở DRAFT."),
    ("OWN-14", "Suspend/archive/delete", "Owner của workflow và transition hợp lệ.", "Thay đổi trạng thái hoặc soft-delete.", "Workflow không còn xuất hiện như published catalog khi bị suspend/archive/delete."),
    ("OWN-15", "Tạo pipeline", "Owner đăng nhập; name/business key hợp lệ.", "Tạo pipeline draft, thêm source, join, transform, output schema và lịch chạy.", "Owner chỉ quản lý pipeline của mình."),
    ("OWN-16", "Dùng connector được cấp", "Admin đã grant connector active cho owner.", "Chọn connector trong Pipeline Designer và test/discover source.", "Không xem credential; chỉ sử dụng connector được phép."),
    ("OWN-17", "Preview/publish/run pipeline", "Pipeline thuộc owner; definition hợp lệ.", "Discover source, preview output, publish rồi run manual hoặc theo lịch.", "Phải preview thành công trước publish; chỉ PUBLISHED mới chạy."),
    ("OWN-18", "Quản lý Dataset/Data Binding", "Sở hữu pipeline; sở hữu workflow published.", "Chọn Dataset, map source field → START field, thêm filter và trigger mode.", "Auto binding chỉ tiêu thụ version mới; record unchanged bị bỏ qua."),
    ("OWN-19", "Theo dõi instance", "Instance thuộc workflow owner quản lý.", "Xem status, current step, history và batch record.", "Dữ liệu được lọc theo quyền runtime."),
]
use_case_table(doc, owner)

doc.add_heading("8. Đặc tả chức năng theo role EDITOR", level=1)
doc.add_paragraph("Editor là cộng tác viên cấu hình definition. Editor không sở hữu quyền quản trị vòng đời workflow.")
editor = [
    ("EDT-01", "Xem workflow được giao", "Editor được owner thêm vào danh sách editor hoặc có quyền view khác.", "Mở danh sách workflow managed và designer.", "Chỉ trả dữ liệu trong phạm vi canView/canEdit."),
    ("EDT-02", "Chỉnh graph draft", "Workflow DRAFT và editor được gán.", "Thêm/sửa/xóa step, cập nhật layout và connection.", "Published workflow không thể chỉnh."),
    ("EDT-03", "Cấu hình form/step", "Có quyền edit draft.", "Cập nhật START fields, Approval, Review, Assignment, Notification, System Action và End.", "Backend kiểm tra quyền workflow, không chỉ role EDITOR."),
    ("EDT-04", "Cấu hình condition", "Connection thuộc draft được giao.", "Chọn field, operator, expected value hoặc biểu thức nâng cao.", "Operator phải tương thích field type."),
    ("EDT-05", "Validate workflow", "Có quyền edit draft.", "Chạy validation và sửa các lỗi được trả về.", "Editor chuẩn bị workflow nhưng không tự publish."),
    ("EDT-06", "Xem version/compare", "Có quyền view workflow family.", "Mở lịch sử và so sánh version.", "Không được restore hoặc tạo version mới."),
    ("EDT-07", "Xem instance liên quan", "Workflow được giao hoặc user liên quan runtime.", "Xem danh sách, chi tiết và history.", "Phạm vi tùy access rule của instance."),
    ("EDT-08", "Gửi request/xử lý task", "Thuộc audience hoặc được gán task.", "Dùng Catalog, form, My Work và Task Detail như user nghiệp vụ.", "Role Editor không tự động cho quyền submit/approve."),
    ("EDT-09", "Các thao tác không được phép", "—", "Không tạo workflow, publish, restore, duplicate, quản lý editor/owner hoặc pipeline.", "Backend từ chối bằng 403 nếu gọi trực tiếp."),
]
use_case_table(doc, editor)

doc.add_heading("9. Đặc tả chức năng theo role VIEWER", level=1)
viewer = [
    ("VIW-01", "Xem Catalog", "Viewer active; có published workflow phù hợp audience.", "Tải catalog và tìm workflow muốn sử dụng.", "Không hiển thị draft/suspended/archived hoặc workflow ngoài audience."),
    ("VIW-02", "Mở form yêu cầu", "Workflow published và viewer được phép submit.", "Tải active version, START config và field definition.", "Form render động theo field type và required."),
    ("VIW-03", "Lưu draft", "Submission mode SINGLE; có thay đổi form.", "Auto-save sau khoảng 900 ms; tải lại draft khi mở lại form.", "Draft gắn user/workflow; field ngoài definition bị loại hoặc từ chối."),
    ("VIW-04", "Xóa draft", "Draft tồn tại.", "User xác nhận và xóa draft của chính mình.", "Form trở về rỗng."),
    ("VIW-05", "Gửi request đơn", "Đủ required field, đúng type và thuộc audience.", "Submit dữ liệu; backend tạo instance và chạy tự động đến step chờ người.", "Sinh request code; xóa draft sau thành công."),
    ("VIW-06", "Gửi batch CSV", "Workflow BATCH; file đúng header/type và không vượt max rows.", "Tải template, import CSV, preview tối đa 50 dòng và submit records.", "Tạo một parent batch instance với trạng thái riêng cho từng record."),
    ("VIW-07", "Theo dõi yêu cầu", "Viewer là requester hoặc có quyền liên quan.", "Xem My Work/Instances, current step, status và journey.", "Chỉ xem instance được phép."),
    ("VIW-08", "Withdraw request", "Viewer là requester; instance RUNNING; nhập lý do.", "Xác nhận thu hồi.", "Instance thành WITHDRAWN; pending task bị cancel; lưu withdrawal reason."),
    ("VIW-09", "Xử lý task", "Viewer được resolve làm assignee.", "Mở Task Detail; nhập comment/field cần thiết; approve, reject hoặc complete.", "Action phải tương thích loại step và task phải còn PENDING."),
    ("VIW-10", "Xem workflow được giao", "Viewer được gán scoped editor hoặc có quyền view.", "Mở danh sách workflow và designer read-only/edit tùy quyền.", "System role VIEWER không đồng nghĩa luôn chỉ đọc; scoped permission quyết định."),
]
use_case_table(doc, viewer)

doc.add_heading("10. Đặc tả xử lý task theo loại step", level=1)
table(doc, ["Loại step", "Actor", "Action hợp lệ", "Cơ chế hoàn thành", "Chuyển nhánh"], [
    ("APPROVAL – Manual", "Fixed user, role/job title hoặc dynamic actor", "APPROVE, REJECT", "Theo ANY, ALL hoặc PERCENTAGE", "APPROVE/REJECT; reject có thể quay lại sửa"),
    ("APPROVAL – Auto", "System", "Tự đánh giá", "Condition đúng/sai", "APPROVE hoặc REJECT"),
    ("REVIEW", "User được cấu hình", "COMPLETE hoặc REJECT", "Theo result mode", "REVIEW_PASS/REVIEW_FAIL"),
    ("ASSIGNMENT", "User hoặc thành viên group", "COMPLETE", "Task hoàn thành theo policy", "DEFAULT/IF/ELSE"),
    ("NOTIFICATION", "System", "Tự động", "Gửi channel được cấu hình", "Đi tiếp sau delivery attempt"),
    ("SYSTEM_ACTION", "System", "Tự động", "Thực hiện action theo failure policy", "Đi tiếp hoặc dừng khi lỗi"),
    ("END", "System", "Tự động", "Đặt outcome cuối", "Kết thúc instance/record"),
], [3.2, 4.0, 3.3, 4.0, 4.2], 7.7)

doc.add_heading("11. Đặc tả Workflow Designer", level=1)
doc.add_heading("11.1. START Step", level=2)
for item in [
    "Mỗi workflow phải có đúng một START step.",
    "Cấu hình submission mode SINGLE hoặc BATCH.",
    "Audience có thể là tất cả user active, user cụ thể, group hoặc system role.",
    "Field hỗ trợ TEXT, NUMBER, DATE, FILE và CHECKBOX.",
    "Field key phải duy nhất trong workflow và được dùng bởi condition/mapping.",
    "BATCH không hỗ trợ FILE; cần dùng TEXT URL nếu cần tham chiếu file.",
]:
    bullet(doc, item)

doc.add_heading("11.2. Connection và condition", level=2)
for item in [
    "Connection type: DEFAULT, IF, ELSE, APPROVE, REJECT, REVIEW_PASS, REVIEW_FAIL.",
    "Approval không sử dụng DEFAULT cho nhánh kết quả; Review cần nhánh pass và tùy mode cần fail.",
    "Mỗi source step chỉ có tối đa một ELSE.",
    "Không dùng đồng thời DEFAULT và IF từ cùng một source.",
    "IF có một hoặc nhiều clause, kết hợp AND hoặc OR; priority quyết định thứ tự đánh giá nhiều nhánh IF.",
    "Operator phụ thuộc field type; FILE chỉ kiểm tra empty/not empty.",
    "Automatic cycle bị cấm; correction loop của manual reject/review fail được cho phép.",
]:
    bullet(doc, item)

doc.add_heading("11.3. Validation trước publish", level=2)
for item in [
    "Đúng một START và ít nhất một END.",
    "Có ít nhất một business step.",
    "Mọi step ngoài START phải có connection vào; mọi step ngoài END phải có connection ra.",
    "Không trùng field key và condition không tham chiếu field đã xóa.",
    "Actor/config bắt buộc phải đầy đủ theo loại step.",
    "Không có automatic cycle không được phép.",
]:
    bullet(doc, item)

doc.add_heading("12. Trạng thái và chuyển trạng thái", level=1)
doc.add_heading("12.1. Workflow", level=2)
table(doc, ["Trạng thái", "Ý nghĩa", "Thao tác chính"], [
    ("DRAFT", "Đang thiết kế, có thể sửa.", "Edit, validate, publish, delete."),
    ("PUBLISHED", "Version hoạt động và nhận request.", "Create next draft, suspend, archive."),
    ("SUSPENDED", "Tạm ngưng sử dụng.", "Không xuất hiện như workflow nhận request mới."),
    ("ARCHIVED", "Lưu trữ.", "Giữ lịch sử, không dùng cho request mới."),
    ("DELETED", "Soft-deleted.", "Ẩn khỏi luồng sử dụng thông thường."),
], [3.1, 7.0, 6.6])

doc.add_heading("12.2. Instance", level=2)
table(doc, ["Trạng thái", "Ý nghĩa"], [
    ("RUNNING", "Đang chạy hoặc chờ task."),
    ("APPROVED", "Kết quả cuối theo outcome approval."),
    ("REJECTED", "Bị từ chối và không còn nhánh correction."),
    ("COMPLETED", "Hoàn thành xử lý."),
    ("CANCELLED", "Bị hủy bởi người có quyền."),
    ("WITHDRAWN", "Requester thu hồi khi đang chạy."),
], [3.5, 13.2])

doc.add_heading("12.3. Task", level=2)
table(doc, ["Trạng thái", "Ý nghĩa"], [
    ("PENDING", "Chờ assignee xử lý."),
    ("COMPLETED", "Đã hoàn thành action hợp lệ."),
    ("CANCELLED", "Bị hủy do instance kết thúc, withdraw hoặc policy đã đạt ngưỡng."),
], [3.5, 13.2])

doc.add_heading("12.4. Pipeline Run", level=2)
table(doc, ["Trạng thái", "Ý nghĩa"], [
    ("QUEUED", "Đã tạo run và chờ thực thi."),
    ("RUNNING", "Đang load/transform/publish dữ liệu."),
    ("RETRY", "Lỗi tạm thời, chờ chạy lại."),
    ("SUCCESS", "Thành công và có record thay đổi."),
    ("NO_CHANGES", "Thành công nhưng không có record thay đổi."),
    ("FAILED", "Thất bại sau số lần retry tối đa."),
], [3.5, 13.2])

doc.add_heading("13. Đặc tả Data Pipeline", level=1)
pipeline_rows = [
    ("DPL-01", "Tạo pipeline", "Admin/Owner; name và business key.", "Tạo DRAFT với source/join/transform/output schema.", "Timezone mặc định Asia/Ho_Chi_Minh."),
    ("DPL-02", "CSV source", "File CSV và alias.", "Upload file, tạo file version/checksum, parse header và record.", "File phải không rỗng; dữ liệu bị giới hạn bởi max records."),
    ("DPL-03", "REST source", "REST connector active; recordPath/pagination.", "GET API, gắn Bearer/API key, đọc JSON array và duyệt trang.", "Chặn HTTP lỗi, trang lặp và record không phải object."),
    ("DPL-04", "PostgreSQL source", "Connector active; một SELECT statement.", "Mở read-only JDBC connection; set timeout/max rows; đọc ResultSet.", "Không cho nhiều statement hoặc câu lệnh ngoài SELECT."),
    ("DPL-05", "Join", "Ít nhất hai source; join key hợp lệ.", "Join source chính với source phải theo thứ tự cấu hình.", "INNER/LEFT/FULL; kiểm tra ONE_TO_ONE/ONE_TO_MANY/MANY_TO_ONE."),
    ("DPL-06", "Transform", "Có input field.", "Áp dụng tuần tự filter/cast/trim/aggregate và operation nâng cao.", "Guided UI chỉ hiển thị một phần; advanced JSON mở rộng khả năng engine."),
    ("DPL-07", "Output schema", "Danh sách field output và business key.", "Project/rename/cast/validate required field.", "Business key có thể gồm nhiều cột và phải tạo định danh ổn định."),
    ("DPL-08", "Preview", "Pipeline source/config hợp lệ.", "Chạy source, join, transform và output trên dữ liệu thực.", "Pipeline chuyển PREVIEWED khi preview chính thức thành công."),
    ("DPL-09", "Publish", "Pipeline PREVIEWED.", "Đổi status thành PUBLISHED và tính nextRunAt.", "Chỉ published pipeline mới run."),
    ("DPL-10", "Schedule", "MANUAL, ONCE hoặc DAILY.", "Scheduler quét mỗi phút; ShedLock chống chạy trùng node.", "ONCE cần scheduledAt; DAILY cần dailyTime."),
    ("DPL-11", "Retry", "Run bị lỗi source/join/transform/publish.", "Retry tối đa ba lần với backoff; lưu error stage/message.", "Thất bại cuối thông báo owner và admin."),
    ("DPL-12", "Publish Dataset", "Pipeline execution thành công.", "Tạo Dataset version, checksum từng record và so version trước.", "Phân loại NEW/CHANGED/UNCHANGED; chưa phân loại DELETED."),
    ("DPL-13", "Automatic Binding", "Binding active, trigger AUTO và có version mới.", "Filter record thay đổi, convert type, map START field và submit batch.", "Cập nhật lastConsumedVersion để tránh tiêu thụ lại."),
]
use_case_table(doc, pipeline_rows)

doc.add_heading("14. Quy tắc nghiệp vụ quan trọng", level=1)
rules = [
    ("BR-01", "Published workflow version không được chỉnh sửa trực tiếp."),
    ("BR-02", "Workflow chỉ publish khi validation trả về danh sách lỗi rỗng."),
    ("BR-03", "Chỉ Admin hoặc owner được quản lý lifecycle và editor của workflow."),
    ("BR-04", "User chỉ submit published workflow khi thuộc audience."),
    ("BR-05", "Task action chỉ hợp lệ với assignee, task PENDING và instance RUNNING."),
    ("BR-06", "Approval ALL chờ tất cả assignee; ANY đạt ngay khi một người hoàn thành; PERCENTAGE làm tròn ngưỡng lên."),
    ("BR-07", "Khi policy hoàn thành, các task còn lại cùng activation có thể bị cancel."),
    ("BR-08", "Requester chỉ withdraw instance đang RUNNING và phải cung cấp lý do."),
    ("BR-09", "Batch record đi theo nhánh độc lập nhưng dùng chung parent instance."),
    ("BR-10", "Data Binding yêu cầu workflow ở trạng thái PUBLISHED."),
    ("BR-11", "Pipeline phải PREVIEWED trước khi PUBLISH và phải PUBLISHED trước khi RUN."),
    ("BR-12", "Connector chỉ được dùng bởi Admin, creator hoặc user được grant."),
    ("BR-13", "PostgreSQL pipeline source chỉ chấp nhận một SELECT không có dấu chấm phẩy."),
    ("BR-14", "Dataset record unchanged không tạo lại workflow work item ở version tiếp theo."),
    ("BR-15", "Deadline reminder phải chống gửi trùng cùng task/kênh/mốc nhắc."),
]
table(doc, ["Mã", "Quy tắc"], rules, [2.0, 14.7])

doc.add_heading("15. Ngoại lệ và thông báo lỗi", level=1)
table(doc, ["Tình huống", "Xử lý mong đợi"], [
    ("JWT thiếu/hết hạn/không hợp lệ", "HTTP 401; frontend xóa phiên và yêu cầu đăng nhập lại."),
    ("Không đủ quyền", "HTTP 403; không thay đổi dữ liệu."),
    ("Không tìm thấy resource", "HTTP 404 với thông báo resource/id."),
    ("DTO không hợp lệ", "HTTP 400 và field errors."),
    ("Vi phạm business rule", "HTTP 400 hoặc conflict phù hợp; hiển thị thông báo nghiệp vụ."),
    ("Workflow validation lỗi", "Không publish; trả toàn bộ lỗi để người thiết kế sửa."),
    ("Connector/pipeline source lỗi", "Gắn error stage; retry nếu là scheduled run; hiển thị chi tiết phù hợp."),
    ("External notification lỗi", "Ghi log/delivery state; không được làm mất audit của workflow action."),
], [6.0, 10.7])

doc.add_heading("16. Yêu cầu phi chức năng và giới hạn hiện tại", level=1)
table(doc, ["Nhóm", "Hiện trạng", "Khuyến nghị"], [
    ("Bảo mật", "JWT localStorage; chưa SSO/MFA/rate limit/token revoke.", "Ưu tiên HttpOnly cookie hoặc hardened token strategy, rate limit, SSO/MFA và security audit."),
    ("File", "File form lưu base64 trong DB; CSV lưu bytea.", "Dùng object storage, metadata DB, signed URL và malware scan."),
    ("Hiệu năng", "Pipeline in-memory, giới hạn mặc định 10.000 record.", "Streaming/chunking, worker queue và resource quota."),
    ("Bất đồng bộ", "Manual pipeline run đồng bộ trong HTTP request.", "Job queue, worker pool, progress/cancel API."),
    ("Quan sát", "Logging cơ bản; chưa metrics/health/tracing.", "Actuator, Micrometer, centralized logs và alert."),
    ("Kiểm thử", "Backend unit test tốt; thiếu frontend/E2E/load/integration rộng.", "Bổ sung API security test, Playwright/Cypress và performance test."),
    ("Triển khai", "Chưa có Docker/CI/CD trong repo.", "Container hóa, pipeline build/test/deploy và environment runbook."),
    ("Type safety", "Pipeline API dùng nhiều Map<String,Object>.", "Typed DTO, JSON schema và contract testing."),
], [3.2, 6.6, 6.9], 8)

doc.add_heading("17. Tiêu chí nghiệm thu tổng quát", level=1)
acceptance = [
    "Mỗi chức năng chỉ hiển thị và thực hiện được đúng theo role và workflow-scoped permission.",
    "Gọi API trực tiếp không được vượt qua kiểm tra quyền backend.",
    "Published workflow không bị thay đổi definition bởi bất kỳ role nào.",
    "Workflow hợp lệ chạy đúng nhánh và không tạo task trùng trong cùng activation.",
    "Task chỉ được xử lý một lần và action được ghi lịch sử.",
    "Request đơn/batch được validate field type và required field ở backend.",
    "Pipeline không publish nếu chưa preview thành công và không run nếu chưa published.",
    "Dataset version và checksum bảo đảm không tái xử lý record unchanged.",
    "Credential connector không xuất hiện trong response hoặc log thông thường.",
    "Các lỗi quyền, validation và business rule trả status/message nhất quán cho frontend.",
]
for item in acceptance:
    numbered(doc, item)

doc.add_heading("18. Kết luận", level=1)
doc.add_paragraph(
    "Đặc tả hiện tại xác định Workflow Builder là hệ thống workflow-driven có phân quyền kết hợp system role và quyền theo từng workflow. "
    "Admin quản trị nền tảng; Workflow Owner chịu trách nhiệm vòng đời quy trình và pipeline; Editor cấu hình draft được giao; Viewer là người dùng "
    "nghiệp vụ gửi request và xử lý task. Quyền task là quyền động theo assignee, do đó mọi role đều có thể trở thành người xử lý khi được cấu hình."
)
doc.add_paragraph(
    "Tài liệu này phản ánh phiên bản source code hiện tại và nên được cập nhật version mỗi khi thay đổi actor, workflow rule, API hoặc trạng thái nghiệp vụ."
)

for sec in doc.sections:
    header = sec.header.paragraphs[0]
    header.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    run = header.add_run("Workflow Builder – Functional Specification")
    run.font.name = "Arial"
    run.font.size = Pt(8)
    run.font.color.rgb = RGBColor(120, 120, 120)
    footer = sec.footer.paragraphs[0]
    footer.alignment = WD_ALIGN_PARAGRAPH.CENTER
    footer.add_run("Trang ")
    page = OxmlElement("w:fldSimple")
    page.set(qn("w:instr"), "PAGE")
    footer._p.append(page)

doc.core_properties.title = "Đặc tả chức năng theo role – Workflow Builder"
doc.core_properties.subject = "Mô tả hệ thống, phân quyền và use case theo vai trò"
doc.core_properties.author = "Codex"
doc.core_properties.keywords = "Workflow Builder, functional specification, RBAC, workflow, data pipeline"
doc.save(OUTPUT)
print(OUTPUT)
