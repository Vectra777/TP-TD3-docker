package fr.takima.training.simpleapi.controller;

import fr.takima.training.simpleapi.entity.Department;
import fr.takima.training.simpleapi.entity.Student;

// Only editable fields are accepted; student IDs come from the database or URL.
public record StudentRequest(String firstname, String lastname, DepartmentRequest department) {
    public record DepartmentRequest(Long id, String name) { }

    public Student toStudent() {
        Department selectedDepartment = department == null ? null : Department.builder()
                .id(department.id())
                .name(department.name())
                .build();
        return Student.builder()
                .firstname(firstname)
                .lastname(lastname)
                .department(selectedDepartment)
                .build();
    }
}
