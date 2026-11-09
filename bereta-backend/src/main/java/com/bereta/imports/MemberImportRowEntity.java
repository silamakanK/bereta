package com.bereta.imports;

import com.bereta.imports.extraction.Confidence;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** Ligne d'aperçu d'un brouillon (supprimée à la validation, à l'annulation ou à la purge). */
@Entity
@Table(name = "member_import_rows")
public class MemberImportRowEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "import_id", nullable = false)
    private UUID importId;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "read_first_name", length = 100) private String readFirstName;
    @Column(name = "read_last_name", length = 100) private String readLastName;
    @Column(name = "read_phone", length = 40) private String readPhone;

    @Column(name = "first_name", length = 100) private String firstName;
    @Column(name = "last_name", length = 100) private String lastName;
    @Column(length = 40) private String phone;
    @Column(name = "phone_e164", length = 20) private String phoneE164;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Confidence confidence;

    @Column(length = 255)
    private String remark;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImportRowStatus status = ImportRowStatus.PRETE;

    @Column(nullable = false)
    private boolean included = true;

    /** L'utilisateur a relu la ligne : une lecture incertaine ou un téléphone absent ne la bloque plus. */
    @Column(nullable = false)
    private boolean confirmed;

    @Column(name = "duplicate_member_id")
    private UUID duplicateMemberId;

    @Column(name = "duplicate_of_line")
    private Integer duplicateOfLine;

    @Enumerated(EnumType.STRING)
    @Column(name = "duplicate_action", length = 10)
    private DuplicateAction duplicateAction;

    protected MemberImportRowEntity() {
    }

    public MemberImportRowEntity(UUID importId, int lineNumber) {
        this.importId = importId;
        this.lineNumber = lineNumber;
    }

    public void setRead(String firstName, String lastName, String phone, Confidence confidence, String remark) {
        this.readFirstName = truncate(firstName, 100);
        this.readLastName = truncate(lastName, 100);
        this.readPhone = truncate(phone, 40);
        this.confidence = confidence;
        this.remark = truncate(remark, 255);
    }

    public void setValues(String firstName, String lastName, String phone, String phoneE164) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.phoneE164 = phoneE164;
    }

    public void setDuplicate(UUID memberId, Integer ofLine) {
        this.duplicateMemberId = memberId;
        this.duplicateOfLine = ofLine;
    }

    public boolean isDuplicate() { return duplicateMemberId != null || duplicateOfLine != null; }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public UUID getId() { return id; }
    public UUID getImportId() { return importId; }
    public int getLineNumber() { return lineNumber; }
    public String getReadFirstName() { return readFirstName; }
    public String getReadLastName() { return readLastName; }
    public String getReadPhone() { return readPhone; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getPhone() { return phone; }
    public String getPhoneE164() { return phoneE164; }
    public Confidence getConfidence() { return confidence; }
    public String getRemark() { return remark; }
    public ImportRowStatus getStatus() { return status; }
    public void setStatus(ImportRowStatus status) { this.status = status; }
    public boolean isIncluded() { return included; }
    public void setIncluded(boolean included) { this.included = included; }
    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }
    public UUID getDuplicateMemberId() { return duplicateMemberId; }
    public Integer getDuplicateOfLine() { return duplicateOfLine; }
    public DuplicateAction getDuplicateAction() { return duplicateAction; }
    public void setDuplicateAction(DuplicateAction duplicateAction) { this.duplicateAction = duplicateAction; }
}
