package vn.huflit.spm.domain;

import java.util.List;

/**
 * Luong chinh: TODO -> DOING -> REVIEW (cho nhom truong xac nhan) -> DONE.
 * SUGGESTED: task do thanh vien de xuat, cho nhom truong duyet. CANCELLED: task bi huy (van giu lich su).
 */
public enum TaskStatus {
    SUGGESTED, TODO, DOING, REVIEW, DONE, CANCELLED;

    /** Cac trang thai tinh vao tien do (khong gom de xuat chua duyet va task da huy). */
    public static final List<TaskStatus> ACTIVE = List.of(TODO, DOING, REVIEW, DONE);
}
