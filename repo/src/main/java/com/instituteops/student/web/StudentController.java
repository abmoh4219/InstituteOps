package com.instituteops.student.web;

import com.instituteops.student.model.ClassSessionRefEntity;
import com.instituteops.student.model.CourseClassRefEntity;
import com.instituteops.student.model.StudentModuleService;
import com.instituteops.student.model.StudentProfileEntity;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

@Controller
@Validated
@RequestMapping
public class StudentController {

    private final StudentModuleService studentModuleService;

    public StudentController(StudentModuleService studentModuleService) {
        this.studentModuleService = studentModuleService;
    }

    @GetMapping("/student")
    public String studentList(@RequestParam(required = false) String q, Authentication authentication, Model model) {
        model.addAttribute("query", q == null ? "" : q);
        model.addAttribute("students", studentModuleService.searchStudentsForPrincipal(authentication, q));
        model.addAttribute("canCreateStudent", studentModuleService.canMutateStudentRecord(authentication));
        return "student-list";
    }

    @PostMapping("/student")
    public String createStudent(
        @RequestParam String studentNo,
        @RequestParam String firstName,
        @RequestParam String lastName,
        @RequestParam(required = false) String preferredName,
        @RequestParam LocalDate dateOfBirth,
        @RequestParam(required = false) String contactEmail,
        @RequestParam(required = false) String contactPhone,
        @RequestParam(required = false) String contactAddress,
        @RequestParam(required = false) String emergencyContact,
        Authentication authentication
    ) {
        studentModuleService.assertCanCreateStudent(authentication);
        StudentProfileEntity student = studentModuleService.createStudent(new StudentModuleService.StudentCreateRequest(
            studentNo,
            firstName,
            lastName,
            preferredName,
            dateOfBirth,
            contactEmail,
            contactPhone,
            contactAddress,
            emergencyContact
        ));
        return "redirect:/student/" + student.getId();
    }

    @GetMapping("/student/{id}")
    public String studentRecord(
        @PathVariable Long id,
        @RequestParam(defaultValue = "false") boolean unmask,
        Authentication authentication,
        Model model
    ) {
        studentModuleService.assertCanAccessStudent(authentication, id);
        boolean allowUnmask = unmask && studentModuleService.canUnmask(authentication);
        StudentModuleService.StudentTimelineView timeline = studentModuleService.timeline(id, allowUnmask);
        List<CourseClassRefEntity> classes = studentModuleService.classes();
        model.addAttribute("timeline", timeline);
        model.addAttribute("classes", classes);
        model.addAttribute("allowUnmask", allowUnmask);
        model.addAttribute("canMutate", studentModuleService.canMutateStudentRecord(authentication));
        return "student-view";
    }

    @PostMapping("/student/{id}/status")
    public String updateStatus(@PathVariable Long id, @RequestParam String status, Authentication authentication) {
        studentModuleService.assertCanMutateStudentRecord(authentication, id);
        studentModuleService.updateStatus(id, status);
        return "redirect:/student/" + id;
    }

    @PostMapping("/student/{id}/delete")
    public String deleteStudent(@PathVariable Long id, Authentication authentication) {
        studentModuleService.assertCanMutateStudentRecord(authentication, id);
        studentModuleService.softDeleteStudent(id);
        return "redirect:/student";
    }

    @PostMapping("/student/{id}/restore")
    public String restoreStudent(@PathVariable Long id, Authentication authentication) {
        studentModuleService.assertCanMutateStudentRecord(authentication, id);
        studentModuleService.restoreStudent(id);
        return "redirect:/student/" + id;
    }

    @PostMapping("/student/{id}/enroll")
    public String enroll(
        @PathVariable Long id,
        @RequestParam Long classId,
        @RequestParam String enrollmentStatus,
        @RequestParam(required = false) LocalDate completionDate,
        Authentication authentication
    ) {
        studentModuleService.assertCanMutateStudentRecord(authentication, id);
        studentModuleService.enroll(id, new StudentModuleService.EnrollmentCreateRequest(classId, enrollmentStatus, completionDate));
        return "redirect:/student/" + id;
    }

    @PostMapping("/student/{id}/payments")
    public String payment(
        @PathVariable Long id,
        @RequestParam(required = false) Long enrollmentId,
        @RequestParam String paymentMethod,
        @RequestParam BigDecimal amount,
        @RequestParam(defaultValue = "USD") String currencyCode,
        @RequestParam(required = false) String paymentReference,
        @RequestParam(required = false) String note,
        Authentication authentication
    ) {
        studentModuleService.assertCanMutateStudentRecord(authentication, id);
        studentModuleService.recordPayment(
            id,
            new StudentModuleService.PaymentCreateRequest(enrollmentId, paymentMethod, amount, currencyCode, paymentReference, note)
        );
        return "redirect:/student/" + id;
    }

    @PostMapping("/student/{id}/attendance")
    public String attendance(
        @PathVariable Long id,
        @RequestParam Long enrollmentId,
        @RequestParam Long classSessionId,
        @RequestParam String attendanceStatus,
        @RequestParam(required = false) String note,
        Authentication authentication
    ) {
        studentModuleService.assertCanMutateStudentRecord(authentication, id);
        studentModuleService.recordAttendance(
            id,
            new StudentModuleService.AttendanceCreateRequest(enrollmentId, classSessionId, attendanceStatus, note)
        );
        return "redirect:/student/" + id;
    }

    @PostMapping("/student/{id}/comments")
    public String comment(
        @PathVariable Long id,
        @RequestParam(required = false) Long classId,
        @RequestParam(required = false) Long classSessionId,
        @RequestParam String commentText,
        @RequestParam(defaultValue = "INTERNAL") String visibility,
        Authentication authentication
    ) {
        studentModuleService.assertCanMutateStudentRecord(authentication, id);
        studentModuleService.recordComment(id, new StudentModuleService.CommentCreateRequest(classId, classSessionId, commentText, visibility));
        return "redirect:/student/" + id;
    }

    @PostMapping(path = "/student/{id}/homework", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String uploadHomework(
        @PathVariable Long id,
        @RequestParam(required = false) Long classId,
        @RequestParam(required = false) Long classSessionId,
        @RequestParam(required = false) String expectedChecksum,
        @RequestParam("file") MultipartFile file,
        Authentication authentication
    ) {
        studentModuleService.assertCanUploadHomework(authentication, id);
        studentModuleService.uploadHomework(
            id,
            new StudentModuleService.HomeworkUploadRequest(classId, classSessionId, expectedChecksum),
            file
        );
        return "redirect:/student/" + id;
    }

    @ResponseBody
    @GetMapping("/api/students")
    public List<StudentSummary> apiSearch(@RequestParam(required = false) String q, Authentication authentication) {
        return studentModuleService.searchStudentsForPrincipal(authentication, q).stream()
            .map(s -> new StudentSummary(s.getId(), s.getStudentNo(), s.getFirstName(), s.getLastName(), s.getStatus(), s.getMaskedEmail(), s.getMaskedPhone()))
            .toList();
    }

    @ResponseBody
    @GetMapping("/api/students/{id}/timeline")
    public StudentModuleService.StudentTimelineView apiTimeline(
        @PathVariable Long id,
        @RequestParam(defaultValue = "false") boolean unmask,
        Authentication authentication
    ) {
        studentModuleService.assertCanAccessStudent(authentication, id);
        boolean allowUnmask = unmask && studentModuleService.canUnmask(authentication);
        return studentModuleService.timeline(id, allowUnmask);
    }

    @ResponseBody
    @GetMapping("/api/classes/{classId}/sessions")
    public List<ClassSessionRefEntity> apiSessions(@PathVariable Long classId, Authentication authentication) {
        studentModuleService.assertCanAccessClass(authentication, classId);
        return studentModuleService.sessionsForClass(classId);
    }

    public record StudentPaymentApiRequest(
        Long enrollmentId,
        @NotBlank String paymentMethod,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        String currencyCode,
        String paymentReference,
        String note
    ) {
    }

    public record StudentSummary(
        Long id,
        String studentNo,
        String firstName,
        String lastName,
        String status,
        String maskedEmail,
        String maskedPhone
    ) {
    }
}
