package com.resumeai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

/**
 * 结构化简历快照：锚点保真校验（FidelityGate）的事实来源。
 * 锚点字段 = 公司名/学校名/证书/时间区间/量化数字——只允许格式规整，不允许增删。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResumeSnapshot {
    @com.fasterxml.jackson.annotation.JsonProperty("work_exps")
    private List<WorkExperience> workExps = new ArrayList<>();
    @com.fasterxml.jackson.annotation.JsonProperty("educations")
    private List<Education> educations = new ArrayList<>();
    private List<String> certificates = new ArrayList<>();
    /** 预留：用户在表单里已确认的其他事实锚点。 */
    private List<String> skills = new ArrayList<>();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WorkExperience {
        private String company;
        private String title;
        private String description;
        private String dateRange;

        public String getCompany() { return company; }
        public void setCompany(String company) { this.company = company; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getDateRange() { return dateRange; }
        public void setDateRange(String dateRange) { this.dateRange = dateRange; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Education {
        private String school;
        private String degree;
        private String dateRange;

        public String getSchool() { return school; }
        public void setSchool(String school) { this.school = school; }
        public String getDegree() { return degree; }
        public void setDegree(String degree) { this.degree = degree; }
        public String getDateRange() { return dateRange; }
        public void setDateRange(String dateRange) { this.dateRange = dateRange; }
    }

    public List<WorkExperience> getWorkExps() { return workExps; }
    public void setWorkExps(List<WorkExperience> workExps) { this.workExps = workExps; }

    public List<Education> getEducations() { return educations; }
    public void setEducations(List<Education> educations) { this.educations = educations; }

    public List<String> getCertificates() { return certificates; }
    public void setCertificates(List<String> certificates) { this.certificates = certificates; }

    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills; }
}
