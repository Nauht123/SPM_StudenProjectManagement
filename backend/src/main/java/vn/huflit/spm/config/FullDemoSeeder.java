package vn.huflit.spm.config;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.huflit.spm.domain.*;

import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Opt-in, additive demo fixture. All records are synthetic, not evidence of real work. */
@Component
@Order(2)
public class FullDemoSeeder implements CommandLineRunner {
    private static final String PREFIX = "[DEMO] ";
    private final EntityManager em;
    private final PasswordEncoder encoder;
    private final boolean enabled;
    private final Path root;
    private LocalDate today;
    private LocalDateTime start;

    public FullDemoSeeder(EntityManager em, PasswordEncoder encoder,
                          @Value("${app.seed-full-demo-data:false}") boolean enabled,
                          @Value("${app.upload-dir}") String uploadDir) {
        this.em = em; this.encoder = encoder; this.enabled = enabled;
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    private <T> T add(T entity) { em.persist(entity); return entity; }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(String... args) throws Exception {
        if (!enabled) return;
        // The first dedicated user doubles as a persistent completion marker. All rows commit together.
        long existing = em.createQuery("select count(u) from User u where u.username like :p", Long.class)
                .setParameter("p", "spmdemo.%").getSingleResult();
        if (existing > 0) {
            System.out.println("FULL DEMO: namespace already exists; skipping to protect existing data.");
            return;
        }
        long codes = em.createQuery("select count(u) from User u where u.studentCode like :p", Long.class)
                .setParameter("p", "SPMDEMO%").getSingleResult();
        if (codes > 0) throw new IllegalStateException("FULL DEMO: student code namespace already exists");
        today = LocalDate.now(); start = today.minusDays(45).atTime(9, 0);
        String hash = encoder.encode("SpmDemo2026!");
        User[] lecturers = new User[3];
        String[] lecturerNames = {"Trần Minh An", "Lê Thu Hà", "Phạm Quốc Bình"};
        for (int i=0;i<3;i++) lecturers[i]=user("gv"+(i+1), lecturerNames[i], null, Role.LECTURER, hash);
        String[] names = {"Nguyễn Minh Anh","Trần Gia Bảo","Lê Thanh Bình","Phạm Khánh Chi","Hoàng Đức Duy","Võ Ngọc Diệp","Đặng Hải Đăng","Bùi Minh Đức","Đỗ Thu Giang","Ngô Quang Huy","Dương Khánh Hân","Huỳnh Minh Khang","Nguyễn Tuấn Kiệt","Trần Mai Lan","Lê Hoàng Long","Phạm Thảo Ly","Vũ Nhật Minh","Đinh Bảo Ngọc","Nguyễn Thanh Phong","Trần Quỳnh Phương","Lê Đức Quân","Phan Minh Quân","Võ Thanh Tâm","Hoàng Anh Thư","Bùi Minh Trang","Đặng Quốc Việt","Ngô Hải Yến","Đỗ Gia Hân","Lý Minh Tâm","Hồ Anh Tuấn"};
        List<User> students = new ArrayList<>();
        for (int i=0;i<30;i++) students.add(user(String.format("sv%02d",i+1), names[i],String.format("SPMDEMO%03d",i+1),Role.STUDENT,hash));
        String[] titles={"Hệ thống quản lý đồ án sinh viên","Website thương mại điện tử","Hệ thống đặt lịch khám bệnh","Hệ thống quản lý thư viện","Ứng dụng quản lý chi tiêu","Hệ thống quản lý nhà hàng","Hệ thống quản lý khóa học","Website tuyển dụng","Ứng dụng quản lý kho","Cổng thông tin sự kiện","Hệ thống đặt phòng","Ứng dụng quản lý công việc"};
        String[] descriptions={"Quản lý đề tài, nhóm, tiến độ và bài nộp.","Danh mục sản phẩm, giỏ hàng và đơn hàng.","Quản lý lịch hẹn, bác sĩ và bệnh nhân giả lập.","Tra cứu sách, mượn trả và thống kê.","Ghi nhận thu chi và báo cáo ngân sách.","Quản lý thực đơn, bàn và đơn gọi món.","Quản lý khóa học, bài học và tiến độ.","Đăng tin, hồ sơ và ứng tuyển.","Nhập xuất hàng và tồn kho.","Đăng ký và quản lý sự kiện.","Tìm kiếm phòng và quản lý đặt chỗ.","Lập kế hoạch và theo dõi công việc."};
        Topic[] topics = new Topic[12];
        int[] owners={0,0,0,1,0,1,2,2,1,1,2,2};
        for(int i=0;i<12;i++) { Topic t=new Topic(); t.setTitle(PREFIX+titles[i]); t.setDescription(descriptions[i]); t.setLecturer(lecturers[owners[i]]); t.setMaxGroups(2); t.setSemester("HK1 2026-2027"); topics[i]=add(t); }
        String[] groups={"Alpha","Beta","Gamma","Delta","Epsilon","Zeta","Omega","Sigma"};
        for(int i=0;i<8;i++) {
            List<User> members=students.subList(i*3,i*3+3);
            Team team=new Team(); team.setName(PREFIX+groups[i]); team.setLeader(members.get(0)); team.getMembers().addAll(members); team.setCreatedAt(start); add(team);
            for(User u:members) { TeamMemberHistory h=new TeamMemberHistory(); h.setTeam(team); h.setMember(u); h.setActor(team.getLeader()); h.setAction(u==team.getLeader()?MemberAction.TEAM_CREATED:MemberAction.ADDED); h.setCreatedAt(start); add(h); }
            Registration reg=new Registration(); reg.setTeam(team); reg.setTopic(topics[i]); reg.setStatus(i==3?RegStatus.PENDING:RegStatus.APPROVED); reg.setNote("Dữ liệu giả lập phục vụ demo học phần."); reg.setCreatedAt(start.plusDays(1)); add(reg);
            notifyUser(team.getLeader(),i==3?"Đăng ký đề tài đang chờ giảng viên duyệt.":"Đề tài đã được giảng viên duyệt.",start.plusDays(2));
            notifyUser(topics[i].getLecturer(),"Nhóm "+team.getName()+" đã gửi đăng ký đề tài.",start.plusDays(1));
            if(i==3) continue; // pending registration has no execution data
            seedWork(team,members,topics[i],i);
            if(i==5 || i==6 || i==7) { TeamChangeRequest r=new TeamChangeRequest(); r.setTeam(team); r.setRequestedBy(team.getLeader()); r.setType(TeamChangeType.ADD_MEMBER); r.setNewMember(students.get(24+i-5)); r.setReason("[DEMO] Đề xuất bổ sung thành viên hỗ trợ kiểm thử."); r.setCreatedAt(today.minusDays(1).atTime(10,0)); add(r); notifyUser(topics[i].getLecturer(),"Có yêu cầu thay đổi thành viên từ "+team.getName(),r.getCreatedAt()); }
        }
        em.flush();
        System.out.println("FULL DEMO: created 3 lecturers, 30 students, 12 topics, 8 teams; synthetic fixtures ready.");
    }

    private User user(String suffix,String name,String code,Role role,String hash) {
        User u=new User(); u.setUsername("spmdemo."+suffix); u.setFullName(name); u.setStudentCode(code); u.setRole(role); u.setPassword(hash); return add(u);
    }
    private void notifyUser(User u,String message,LocalDateTime time) {
        Notification n=new Notification(); n.setUser(u); n.setMessage(PREFIX+message); n.setCreatedAt(time); add(n);
    }
    private Milestone milestone(Team team,Milestone parent,String title,LocalDate due,LocalDateTime created) {
        Milestone m=new Milestone(); m.setTeam(team); m.setParent(parent); m.setTitle(title); m.setDescription("[DEMO] "+title+" và minh chứng kết quả."); m.setDueDate(due); m.setCreatedAt(created); return add(m);
    }
    private void seedWork(Team team,List<User> members,Topic topic,int group) throws Exception {
        String[] phases={"Phân tích yêu cầu","Thiết kế và lập trình","Kiểm thử và bàn giao"};
        String[][] tasks={{"Khảo sát nghiệp vụ","Viết đặc tả yêu cầu","Vẽ Use Case","Thiết kế dữ liệu","Review tài liệu"},{"Xây dựng API","Thiết kế giao diện","Tích hợp cơ sở dữ liệu","Tích hợp frontend/backend","Review mã nguồn"},{"Viết test case","Kiểm thử API","Kiểm thử giao diện","Sửa lỗi","Hoàn thiện báo cáo"}};
        for(int p=0;p<3;p++) {
            LocalDate due=today.plusDays(group==2 ? -24+p*9 : -10+p*12);
            if(group==1) due=today.minusDays(12-p*3);
            if(group==4 && p==0) due=today.minusDays(18);
            LocalDateTime created=start.plusDays(3+p*7);
            Milestone main=milestone(team,null,phases[p],due,created);
            Milestone sub=milestone(team,main,"Thực hiện "+phases[p].toLowerCase(Locale.ROOT),due.minusDays(1),created.plusHours(1));
            for(int j=0;j<5;j++) {
                int k=p*5+j;
                int done= switch(group){case 0->10;case 1->3;case 2->15;case 4->7;case 5->6;case 6->8;default->5;};
                TaskStatus state=k<done?TaskStatus.DONE:k==done?TaskStatus.REVIEW:k==done+1?TaskStatus.DOING:TaskStatus.TODO;
                boolean revise=group==7 && k==done+1;
                seedTask(sub,team,members.get(j%3),tasks[p][j],state,created.plusHours(2),j,revise);
            }
            // Completed/partially delivered phases only; no future-dated submissions.
            if(p==0 || group==2 || (p==1 && group!=1)) {
                LocalDateTime submitted=due.minusDays(2).atTime(10,0);
                if(submitted.isAfter(today.minusDays(1).atTime(10,0))) submitted=today.minusDays(1).atTime(10,0);
                if(group==4 && p==0) submitted=due.plusDays(2).atTime(10,0);
                seedSubmission(main,team,members,topic,1,submitted);
                if(p==0) seedSubmission(main,team,members,topic,2,submitted.plusHours(2));
            }
            if(p==0 && group!=1) {
                for(User a:members) for(User b:members) if(a!=b) { PeerEvaluation e=new PeerEvaluation(); e.setMilestone(main); e.setEvaluator(a); e.setEvaluatee(b); e.setContributionScore(4+(int)(b.getId()%2)); e.setWorkloadScore(3+(int)(b.getId()%3)); e.setComment("[DEMO] Phối hợp tốt, có minh chứng công việc."); e.setCreatedAt(today.minusDays(1).atTime(14,0)); e.setUpdatedAt(e.getCreatedAt()); add(e); }
            }
        }
    }
    private void history(Task task,User actor,TaskAction action,TaskStatus from,TaskStatus to,LocalDateTime time) {
        TaskHistory h=new TaskHistory(); h.setTask(task); h.setActor(actor); h.setAction(action); h.setFromStatus(from); h.setToStatus(to); h.setCreatedAt(time); h.setDetail("[DEMO] "+action.name()); add(h);
    }
    private void seedTask(Milestone m,Team team,User assignee,String title,TaskStatus state,LocalDateTime time,int j,boolean revise) {
        User leader=team.getLeader(); Task t=new Task(); t.setMilestone(m); t.setTitle(title); t.setDescription("[DEMO] Thực hiện "+title.toLowerCase(Locale.ROOT)+" và ghi nhận kết quả."); t.setAssignee(assignee); t.setAssignedBy(leader); t.setCreatedBy(leader); t.setCreatedAt(time); t.setAssignedAt(time.plusMinutes(10)); t.setDueDate(m.getDueDate()); t.setDifficulty(2+j%4); t.setWeight(2+j); t.setWorkloadHours(3+j*2); t.setStatus(state); add(t);
        history(t,leader,TaskAction.CREATED,null,TaskStatus.TODO,time); history(t,leader,TaskAction.ASSIGNED,TaskStatus.TODO,TaskStatus.TODO,t.getAssignedAt());
        TaskAssignmentHistory a=new TaskAssignmentHistory(); a.setTask(t); a.setAssignee(assignee); a.setAssignedBy(leader); a.setAssignedAt(t.getAssignedAt()); add(a);
        if(state!=TaskStatus.TODO) history(t,assignee,TaskAction.STARTED,TaskStatus.TODO,TaskStatus.DOING,time.plusHours(1));
        if(state==TaskStatus.DONE || state==TaskStatus.REVIEW || revise) {
            LocalDateTime submitted=time.plusDays(1); t.setSubmittedAt(submitted); t.setResultNote("[DEMO] Đã hoàn thành bản kết quả để review.");
            history(t,assignee,TaskAction.SUBMITTED,TaskStatus.DOING,TaskStatus.REVIEW,submitted);
            TaskSubmissionHistory s=new TaskSubmissionHistory(); s.setTask(t); s.setAttemptNo(1); s.setSubmittedBy(assignee); s.setNote(t.getResultNote()); s.setSubmittedAt(submitted);
            if(state==TaskStatus.DONE) { t.setConfirmedAt(submitted.plusHours(2)); t.setQualityRating(4+j%2); s.setOutcome(SubmissionOutcome.APPROVED); s.setReviewedBy(leader); s.setReviewedAt(t.getConfirmedAt()); history(t,leader,TaskAction.CONFIRMED,TaskStatus.REVIEW,TaskStatus.DONE,t.getConfirmedAt()); }
            if(revise) { t.setRevisionCount(1); t.setReviewNote("[DEMO] Bổ sung trường hợp kiểm thử biên."); s.setOutcome(SubmissionOutcome.REVISION_REQUESTED); s.setReviewedBy(leader); s.setReviewedAt(submitted.plusHours(2)); s.setReviewNote(t.getReviewNote()); history(t,leader,TaskAction.REVISION_REQUESTED,TaskStatus.REVIEW,TaskStatus.DOING,s.getReviewedAt()); }
            add(s);
        }
        if(j<2) { TaskComment c=new TaskComment(); c.setTask(t); c.setAuthor(assignee); c.setContent("[DEMO] Đã cập nhật kế hoạch và trao đổi với nhóm."); c.setCreatedAt(time.plusHours(3)); add(c); }
        if(j<2) notifyUser(assignee,"Bạn được giao task: "+title,t.getAssignedAt());
    }
    private void seedSubmission(Milestone m,Team team,List<User> members,Topic topic,int version,LocalDateTime time) throws Exception {
        String name="bao-cao-demo-v"+version+".txt";
        // UUID avoids overwriting any existing file, even after a rolled-back seed attempt.
        Path path=root.resolve("spm-full-demo").resolve(UUID.randomUUID()+"-"+name);
        Files.createDirectories(path.getParent());
        Files.writeString(path,"DỮ LIỆU GIẢ LẬP - KHÔNG PHẢI MINH CHỨNG THỰC TẾ\nNhóm: "+team.getName()+"\nĐề tài: "+topic.getTitle()+"\nMilestone: "+m.getTitle()+"\nPhiên bản: "+version+"\nNội dung: Tài liệu mẫu phục vụ kiểm thử tải xuống và phản hồi.\n",StandardOpenOption.CREATE_NEW);
        // Files are outside the DB transaction: remove newly created files on rollback.
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){
            @Override public void afterCompletion(int status){ if(status!=STATUS_COMMITTED) try { Files.deleteIfExists(path); } catch(java.io.IOException ex){ System.err.println("FULL DEMO: could not clean "+path); } }
        });
        Submission s=new Submission(); s.setMilestone(m); s.setSubmittedBy(team.getLeader()); s.setVersion(version); s.setFileName(name); s.setStoredPath(root.relativize(path).toString()); s.setNote("[DEMO] Bản nộp số "+version); s.setSubmittedAt(time); s.setLate(time.isAfter(m.getDueDate().plusDays(1).atStartOfDay())); add(s);
        for(User u:members) { SubmissionContributor c=new SubmissionContributor(); c.setSubmission(s); c.setUser(u); add(c); }
        Feedback f=new Feedback(); f.setSubmission(s); f.setLecturer(topic.getLecturer()); f.setComment(version==1?"[DEMO] Cần bổ sung minh chứng và mô tả nghiệp vụ.":"[DEMO] Đã tiếp nhận bản cập nhật, tiếp tục hoàn thiện."); f.setCreatedAt(time.plusMinutes(30)); add(f);
        notifyUser(team.getLeader(),"Giảng viên đã phản hồi bài nộp "+m.getTitle(),f.getCreatedAt());
    }
}
