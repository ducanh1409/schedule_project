package com.fpt.edu.schedule.service.impl;


import com.fpt.edu.schedule.ai.lib.Class;
import com.fpt.edu.schedule.ai.lib.ExpectedSlot;
import com.fpt.edu.schedule.ai.lib.ExpectedSubject;
import com.fpt.edu.schedule.ai.lib.Room;
import com.fpt.edu.schedule.ai.lib.Subject;
import com.fpt.edu.schedule.ai.lib.*;
import com.fpt.edu.schedule.ai.model.GeneticAlgorithm;
import com.fpt.edu.schedule.ai.model.Model;
import com.fpt.edu.schedule.ai.model.Train;
import com.fpt.edu.schedule.common.enums.StatusLecturer;
import com.fpt.edu.schedule.common.exception.InvalidRequestException;
import com.fpt.edu.schedule.dto.Runs;
import com.fpt.edu.schedule.model.*;
import com.fpt.edu.schedule.repository.base.*;
import com.fpt.edu.schedule.service.base.SemesterService;
import com.fpt.edu.schedule.service.base.SubjectService;
import com.fpt.edu.schedule.service.base.TimetableService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class TimetableServiceImpl implements TimetableService {
    public static final int POPULATION_SIZE = 150;
    public static Map<String, GeneticAlgorithm> map = new HashMap<>();
    SemesterService semesterService;
    TimetableRepository timetableRepository;
    ExpectedRepository expectedRepository;
    LecturerRepository lecturerRepository;
    SubjectService subjectService;
    SubjectRepository subjectRepository;
    SlotRepository slotRepository;
    SemesterRepository semesterRepository;
    TimetableDetailRepository timetableDetailRepository;
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    ApplicationEventPublisher applicationEventPublisher;


    @Override
    public Timetable save(Timetable timeTable) {
        return null;
    }

    @Override
    public Timetable findBySemester(Semester semester) {
        Timetable timetable = timetableRepository.findBySemester(semester);
        if (timetable == null) {
            throw new InvalidRequestException("Don't have data for this timetable");
        }
        return timetable;
    }

    @Async
    @Override
    public void autoArrange(int semesterId, String lecturerId) {

        Vector<Teacher> teacherModels = new Vector<>();
        Vector<Subject> subjectModels = new Vector<>();
        Vector<com.fpt.edu.schedule.ai.lib.Class> classModels = new Vector<>();
        Vector<ExpectedSlot> expectedSlotModels = new Vector<>();
        Vector<ExpectedSubject> expectedSubjectModel = new Vector<>();
        Vector<SlotGroup> slotGroups = new Vector<>();
        Lecturer lecturer = lecturerRepository.findByGoogleId(lecturerId);
        Semester semester = semesterService.findById(semesterId);
        Timetable timetable = findBySemester(semester);
//        List<TimetableDetail> timetableDetails = timetable.getTimetableDetails().stream()
//                .filter(i -> i.getSubject().getDepartment().equals(lecturer.getDepartment()) && i.getLecturer()!=null)
//                .collect(Collectors.toList());
//        timetableDetails.forEach(i->{
//            i.setLecturer(null);
//            timetableDetailRepository.save(i);
//        });

        convertData(teacherModels, subjectModels, classModels, expectedSlotModels, expectedSubjectModel, semesterId, lecturerId, slotGroups, lecturer, semester, timetable);
//        importDataFromFile();
        Model model = new Model(teacherModels, slotGroups, subjectModels, classModels, expectedSlotModels, expectedSubjectModel);
        Train train = new Train();
        GeneticAlgorithm ga = applicationContext.getBean(GeneticAlgorithm.class);
        ga.setGeneration(0);
        ga.setModel(model);
        ga.setTrain(train);
        ga.setRun(true);
        ga.setLecturerId(lecturerId);
        if (map.get(lecturerId) != null && !map.get(lecturerId).isRun()) {
            map.remove(lecturerId);
        }
        if (map.get(lecturerId) != null && map.get(lecturerId).isRun()) {
            throw new InvalidRequestException("Running arrange !");
        }
        map.put(lecturerId, ga);
        ga.start();
        System.out.println("-------------------------Start-----LecturerId :" + lecturerId);
    }

    @Override
    public void stop(String lecturerId) {
        map.get(lecturerId).stop();
        System.out.println("-------------------------Stop-----LecturerId :" + lecturerId);

    }

    @Override
    public QueryParam.PagedResultSet<Runs> getGenerationInfo(String lecturerId, int page, int limit) {
        QueryParam.PagedResultSet<Runs> pagedResultSet = new QueryParam.PagedResultSet<>();
        GeneticAlgorithm ge = map.get(lecturerId);

        pagedResultSet.setRunning(ge == null ? false : ge.isRun());
        if (ge == null) {
            pagedResultSet.setResults(new ArrayList<>());
            pagedResultSet.setPage(page);
            return pagedResultSet;
        }
        Map<Integer, Runs> mapRuns = ge.getGenInfos();
        List<Runs> runsList = mapRuns.values().stream().sorted(Comparator.comparing(Runs::getGeneration)).collect(Collectors.toList());
        pagedResultSet.setTotalCount(runsList.size());
        List<Runs> runsListComplete = runsList.stream()
                .skip((page - 1) * limit)
                .limit(limit)
                .collect(Collectors.toList());
        pagedResultSet.setResults(runsListComplete);
        pagedResultSet.setSize(runsListComplete.size());
        pagedResultSet.setPage(page);

        return pagedResultSet;
    }

    @Override
    public void setDefaultTimetable(int runId, String lecturerId, int semesterId) {
        Runs runs = map.get(lecturerId).getGenInfos().get(runId);
        List<TimetableDetail> timetableDetails = runs.getTimetableDetails();
        Timetable timetable = timetableRepository.findBySemester(semesterRepository.findById(semesterId));
        timetable.setTimetableDetails(timetableDetails);
        timetableRepository.save(timetable);
    }

    private void convertData(Vector<Teacher> teacherModels, Vector<Subject> subjectModels,
                             Vector<com.fpt.edu.schedule.ai.lib.Class> classModels, Vector<ExpectedSlot> expectedSlotModels,
                             Vector<ExpectedSubject> expectedSubjectModel,
                             int semesterId, String lecturerId, Vector<SlotGroup> slots, Lecturer lecturer, Semester semester, Timetable timetable) {

        List<TimetableDetail> timetableDetails = timetable.getTimetableDetails().stream()
                .filter(i -> i.getSubject().getDepartment().equals(lecturer.getDepartment()) && !Character.isAlphabetic(i.getSubject().getCode().charAt(i.getSubject().getCode().length() - 1)))
                .collect(Collectors.toList());
        List<Expected> expected = expectedRepository.findAllBySemester(semester);
        List<com.fpt.edu.schedule.model.Subject> subjects = subjectService.getAllSubjectBySemester(semesterId, lecturerId);
        //teacher model
        List<Lecturer> lecturers = lecturerRepository.findAllByDepartmentAndStatus(lecturer.getDepartment(), StatusLecturer.ACTIVATE).stream()
                .filter(i -> expectedRepository.findBySemesterAndLecturer(semester, i) != null)
                .collect(Collectors.toList());
        ;

        lecturers.forEach(i -> {
            Expected expectedEach = expectedRepository.findBySemesterAndLecturer(semester, i);
            teacherModels.add(new Teacher(i.getEmail(), i.getFullName(), i.getId(), i.isFullTime() ? 1 : 0, expectedEach.getExpectedNote().getExpectedNumOfClass(), expectedEach.getExpectedNote().getMaxConsecutiveSlot(), i.getQuotaClass()));
        });
        //expected model
        expected.stream().filter(i -> lecturers.contains(i.getLecturer())).forEach(i -> {
            i.getExpectedSlots().forEach(s -> {
                expectedSlotModels.add(new ExpectedSlot(s.getExpected().getLecturer().getId(), slotRepository.findByName(s.getSlotName()).getId(), s.getLevelOfPrefer()));
            });
            i.getExpectedSubjects().forEach(s -> {
                expectedSubjectModel.add(new ExpectedSubject(s.getExpected().getLecturer().getId(), subjectRepository.findByCode(s.getSubjectCode()).getId(), s.getLevelOfPrefer()));
            });
        });
        timetableDetails.forEach(i -> {
            classModels.add(new Class(i.getClassName().getName(), i.getSlot().getId(), i.getSubject().getId(), new Room(i.getRoom().getName()), i.getId()));
        });
        subjects.forEach(i -> {
            subjectModels.add(new Subject(i.getCode(), i.getId()));
        });


        SlotGroup m246 = new SlotGroup(3);
        m246.addSlot(new com.fpt.edu.schedule.ai.lib.Slot("M1", 3));
        m246.addSlot(new com.fpt.edu.schedule.ai.lib.Slot("M2", 2));
        m246.addSlot(new com.fpt.edu.schedule.ai.lib.Slot("M3", 1));
        SlotGroup e246 = new SlotGroup(3);

        e246.addSlot(new com.fpt.edu.schedule.ai.lib.Slot("E1", 10));
        e246.addSlot(new com.fpt.edu.schedule.ai.lib.Slot("E2", 7));
        e246.addSlot(new com.fpt.edu.schedule.ai.lib.Slot("E3", 8));

        SlotGroup m35 = new SlotGroup(1);

        m35.addSlot(new com.fpt.edu.schedule.ai.lib.Slot("M4", 4));
        m35.addSlot(new com.fpt.edu.schedule.ai.lib.Slot("M5", 5));

        SlotGroup e35 = new SlotGroup(1);

        e35.addSlot(new com.fpt.edu.schedule.ai.lib.Slot("E4", 9));
        e35.addSlot(new com.fpt.edu.schedule.ai.lib.Slot("E5", 6));
        slots.add(m246);
        slots.add(e246);
        slots.add(m35);
        slots.add(e35);

    }


    private void importDataFromFile() {
        try {

            File slotFile = new File("D:\\Project\\schedule\\Schedule_project\\src\\main\\java\\com\\fpt\\edu\\schedule\\ai\\data\\teacher_slot_real.xml");
            File subjectFile = new File("D:\\Project\\schedule\\Schedule_project\\src\\main\\java\\com\\fpt\\edu\\schedule\\ai\\data\\teacher_subject_real.xml");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder buider = factory.newDocumentBuilder();
            Document doc = buider.parse(slotFile);
            List<String> slot = new ArrayList<>();

            Element lst = doc.getDocumentElement();

            NodeList teacherList = lst.getElementsByTagName("Row");

            System.out.println(teacherList);
            System.out.println("----------------------------");
            for (int i = 0; i < teacherList.getLength(); i++) {
                Expected expected = new Expected();
                Node node = teacherList.item(i);
                List<com.fpt.edu.schedule.model.ExpectedSlot> expectedSlots = new ArrayList<>();
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element e = (Element) node;
                    if (i == 0) {
                        for (int j = 1; j <= 10; j++) {
                            slot.add(e.getElementsByTagName("Cell").item(j).getTextContent());
                        }
                        System.out.println("All slot " + slot);
                        continue;
                    }
                    String teacherMail = e.getElementsByTagName("Cell").item(0).getTextContent();
                    Lecturer lecturer = lecturerRepository.findByEmail(teacherMail);
                    System.out.println(e.getElementsByTagName("Cell").item(0).getTextContent());
                    ExpectedNote expectedNote = new ExpectedNote();
                    expectedNote.setExpected(expected);
                    expectedNote.setExpectedNumOfClass(Integer.parseInt(e.getElementsByTagName("Cell").item(slot.size() + 1).getTextContent()));
                    expectedNote.setMaxConsecutiveSlot(Integer.parseInt(e.getElementsByTagName("Cell").item(slot.size() + 2).getTextContent()));

                    for (int t = 0; t < slot.size(); t++) {
                        com.fpt.edu.schedule.model.ExpectedSlot expectedSlot = new com.fpt.edu.schedule.model.ExpectedSlot();
                        int levelPrefer = Integer.parseInt(e.getElementsByTagName("Cell").item(t + 1).getTextContent());
                        expectedSlot.setSlotName(slot.get(t));
                        System.out.println(slot.get(t) + " :" + levelPrefer);
                        expectedSlot.setExpected(expected);
                        expectedSlot.setLevelOfPrefer(Integer.parseInt(e.getElementsByTagName("Cell").item(t + 1).getTextContent()));
                        System.out.println();
                        expectedSlots.add(expectedSlot);


                    }
                    expected.setSemester(semesterRepository.getAllByNowIsTrue());
                    expected.setLecturer(lecturer);
                    expected.setExpectedSlots(expectedSlots);
                    expected.setExpectedNote(expectedNote);
                    expectedRepository.save(expected);

                }
            }
            factory = DocumentBuilderFactory.newInstance();
            buider = factory.newDocumentBuilder();
            doc = buider.parse(subjectFile);
            List<String> subject = new ArrayList<>();

            lst = doc.getDocumentElement();

            NodeList subjectList = lst.getElementsByTagName("Row");

            System.out.println(subjectList);
            System.out.println("----------------------------");
            for (int i = 0; i < subjectList.getLength(); i++) {

                Node node = subjectList.item(i);
                List<com.fpt.edu.schedule.model.ExpectedSubject> expectedSubjects = new ArrayList<>();
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element e = (Element) node;
                    if (i == 0) {
                        for (int j = 1; j <= 17; j++) {
                            String subjectCode = e.getElementsByTagName("Cell").item(j).getTextContent();

                            subject.add(subjectCode);
                        }
                        System.out.println("All subject " + subject);
                        continue;
                    }
                    String teacherMail = e.getElementsByTagName("Cell").item(0).getTextContent();
                    Lecturer lecturer = lecturerRepository.findByEmail(teacherMail);
                    Expected expected = expectedRepository.findBySemesterAndLecturer(semesterRepository.getAllByNowIsTrue(), lecturer);
                    System.out.println(teacherMail);
                    ;

                    for (int t = 0; t < subject.size(); t++) {
                        String subjectCode = subject.get(t);
                        if (subjectRepository.findByCode(subjectCode) == null) {
                            continue;
                        }
                        com.fpt.edu.schedule.model.ExpectedSubject expectedSubject = new com.fpt.edu.schedule.model.ExpectedSubject();
                        expectedSubject.setSubjectCode(subject.get(t));
                        System.out.println("Subject :" + subject.get(t) + " level :" + Integer.parseInt(e.getElementsByTagName("Cell").item(t + 1).getTextContent()));
                        expectedSubject.setExpected(expected);
                        expectedSubject.setLevelOfPrefer(Integer.parseInt(e.getElementsByTagName("Cell").item(t + 1).getTextContent()));
                        System.out.println();
                        expectedSubjects.add(expectedSubject);


                    }
                    expected.setExpectedSubjects(expectedSubjects);
                    expectedRepository.save(expected);

                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}

