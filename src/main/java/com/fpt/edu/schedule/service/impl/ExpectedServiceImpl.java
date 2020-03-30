package com.fpt.edu.schedule.service.impl;


import com.fpt.edu.schedule.common.exception.InvalidRequestException;
import com.fpt.edu.schedule.model.Expected;
import com.fpt.edu.schedule.model.ExpectedSubject;
import com.fpt.edu.schedule.model.Lecturer;
import com.fpt.edu.schedule.model.Subject;
import com.fpt.edu.schedule.repository.base.*;
import com.fpt.edu.schedule.repository.base.QueryParam;
import com.fpt.edu.schedule.service.base.*;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Duc Anh
 * @since 17/3/2020
 */
@Service
@Transactional
@AllArgsConstructor
public class ExpectedServiceImpl implements ExpectedService {
    LecturerService lecturerService;
    SubjectService subjectService;
    SlotService slotService;
    ExpectedRepository expectedRepository;
    ExpectedSlotRepository expectedSlotRepository;
    ExpectedSubjectRepository expectedSubjectRepository;
    ExpectedSlotService expectedSlotService;
    ExpectedSubjectService expectedSubjectService;
    ExpectedNoteRepository expectedNoteRepository;
    SemesterRepository semesterRepository;

    @Override
    public Expected addExpected(Expected expected) {
        expected.setCreatedDate(new Date());
        expected.setUpdatedDate(new Date());
        Lecturer lecturer = lecturerService.getLecturerNameById(expected.getLecturer().getId());
        if (lecturer == null) {
            throw new InvalidRequestException("Don't find lecturer!");
        }
        expected.setSemester(semesterRepository.findById(expected.getSemester().getId()));
        expected.setLecturer(lecturer);
        expected.getExpectedNote().setExpected(expected);
        expected.getExpectedSlots().stream().forEach(i -> i.setExpected(expected));
        expected.getExpectedSubjects().stream().forEach(i -> i.setExpected(expected));
        return expectedRepository.save(expected);
    }

    @Override
    public Expected updateExpected(Expected expected) {
        Expected existedExpected = expectedRepository.findById(expected.getId());
        if (existedExpected == null) {
            throw new InvalidRequestException("Don't find this expected");
        }
        if (expected.getExpectedNote() != null) {
            existedExpected.setExpectedNote(expected.getExpectedNote());
            expectedNoteRepository.removeAllByExpected(existedExpected);
            existedExpected.getExpectedNote().setExpected(existedExpected);
        }
        if (expected.getExpectedSlots() != null) {
            expected.getExpectedSlots().forEach(i -> expectedSlotService.update(i));
        }
        if (expected.getExpectedSubjects() != null) {
            expected.getExpectedSubjects().forEach(i -> expectedSubjectService.update(i));
        }
        existedExpected.setUpdatedDate(new Date());
        return expectedRepository.save(existedExpected);
    }

    @Override
    public List<Expected> findByCriteria(QueryParam queryParam) {
        BaseSpecifications cns = new BaseSpecifications(queryParam);

        return expectedRepository.findAll(cns);
    }

    @Override
    public void removeExpectedById(int expectedId) {
        Expected existedExpected = expectedRepository.findById(expectedId);
        if (existedExpected == null) {
            throw new InvalidRequestException("Don't find this expected");
        }
        expectedRepository.removeExpectedById(existedExpected.getId());
    }

    @Override
    public Expected getExpectedByLecturerAndSemester(String lecturerId, int semesterId) {
        Expected expected = expectedRepository.findBySemesterAndLecturer(semesterRepository.findById(semesterId),
                lecturerService.getLecturerNameById(lecturerId));
        if (expected == null) {
            Expected newExpected = new Expected();
            List<Subject> subjects = subjectService.getAllSubjectBySemester(semesterId);
            List<ExpectedSubject> expectedSubjectList = subjects.stream().map(i -> new ExpectedSubject(i.getCode())).collect(Collectors.toList());
            newExpected.setExpectedSubjects(expectedSubjectList);
            return newExpected;
        }
        return expected;
    }


}
