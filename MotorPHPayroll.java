/*
=====================================================
MOTORPH PAYROLL SYSTEM
Author: Group 13

Description:
This Java program simulates the MotorPH Payroll System. The
system calculates employee payroll based on attendance records.

Main Features:
• Login system for employee and payroll_staff
• Attendance processing using time-in and time-out records
• Payroll computation using two cutoffs:
    - 1st cutoff (1–15)
    - 2nd cutoff (16–end of month)
• Automatic computation of:
    - SSS Contribution (from MotorPH's table)
    - PhilHealth Contribution (from MotorPH's table)
    - PagIBIG Contribution (from MotorPh's table)
    - TRAIN Withholding Tax (from MotorPH's table)
• Payroll report generation
=====================================================
*/

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MotorPHPayroll {

    // ---------------------- GLOBAL VARIABLES ----------------------
    static Scanner sc = new Scanner(System.in);               // For user input
    static Map<String, String[]> employees = new HashMap<>(); // Employee data keyed by employee number
    static Map<String, Map<YearMonth, double[]>> allAttendance = new HashMap<>(); // Nested map for memory efficiency
    static Map<String, Integer> columnMap = new HashMap<>();  // CSV header to index mapping
    static String currentUser;                                // Current logged-in user
    static YearMonth payrollMonth;                            // Current payroll month
    
    // ---------------------- CONSTANTS ----------------------
    static final String EMPLOYEES_FILE = "employees.csv";
    static final String ATTENDANCE_FILE = "attendance.csv";
    static final LocalTime SHIFT_START = LocalTime.of(8, 0); // official shift start
    static final LocalTime SHIFT_END = LocalTime.of(17, 0);  // official shift end
    static final double LUNCH_HOURS = 1.0;                  // 1 hour lunch deduction

    // ---------------------- MAIN FUNCTION ----------------------
    public static void main(String[] args) {
        try {
            loadEmployees();   // Load employee data from CSV
            loadAllAttendance(); // Single-pass loading of all attendance data
            login();           // User login

            // Route user based on role
            if (currentUser.equals("employee")) {
                employeeMenu();
            } else {
                payrollStaffMenu();
            }
        } catch (FileNotFoundException e) {
            System.err.println("Critical Error: Required CSV file missing. " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Critical Error: Could not read or parse CSV file. " + e.getMessage());
        } catch (NumberFormatException e) {
            System.err.println("Data Error: Invalid numeric value found in CSV. " + e.getMessage());
        } catch (ArithmeticException e) {
            System.err.println("Computation Error: " + e.getMessage());
        } catch (RuntimeException e) {
            System.err.println("Unexpected Error: " + e.getMessage());
        }
    }

    // ---------------------- LOGIN PROCESS ----------------------
    static void login() {
        System.out.println("------- MOTORPH PAYROLL SYSTEM -------");
        System.out.print("Username: ");
        String user = sc.nextLine();
    
        System.out.print("Password: ");
        String pass = sc.nextLine();
    
        // Validate username and password
        if ((user.equals("employee") || user.equals("payroll_staff")) && pass.equals("12345")) {
            currentUser = user; // store the logged-in role
        } else {
            System.out.println("Incorrect username and/or password.");
            System.exit(0); // terminate the program
        }
    }

    // ---------------------- EMPLOYEE MENU ----------------------
    static void employeeMenu() {
        System.out.println("\n--- EMPLOYEE MENU ---");
        System.out.println("1. Enter Your Employee Number");
        System.out.println("2. Exit the Program");

        System.out.print("Enter Choice: ");
        String choice = sc.nextLine().trim();

        if (choice.equals("1")) {
            System.out.print("\nEnter Employee No.: ");
            String empNo = sc.nextLine().trim();

            if (!employees.containsKey(empNo)) {
                System.out.println("Employee number does not exist.");
                return;
            }

            // Print employee info header (using reusable method)
            printEmployeeHeader(empNo);

        } else if (choice.equals("2")) {
            System.exit(0);
        }
    }

    // ---------------------- PAYROLL STAFF MENU ----------------------
    static void payrollStaffMenu() {
        while (true) {
            System.out.println("\n--- PAYROLL STAFF MENU ---");
            System.out.println("1. Process Payroll");
            System.out.println("2. Exit the Program");

            System.out.print("Enter Choice: ");
            String choice = sc.nextLine().trim();

            if (choice.equals("1")) {
                System.out.println("\nProcess Payroll Options:");
                System.out.println("1. One Employee");
                System.out.println("2. All Employees");
                System.out.println("3. Back to Main Menu");

                System.out.print("Enter Choice: ");
                String subChoice = sc.nextLine().trim();

                if (subChoice.equals("1")) {
                    System.out.print("\nEnter Employee Number: ");
                    String empNo = sc.nextLine().trim();

                    if (!employees.containsKey(empNo)) {
                        System.out.println("Employee number does not exist.");
                        continue;
                    }

                    // Process payroll for each month for one employee
                    processPayrollForEmployee(empNo);

                } else if (subChoice.equals("2")) {
                    // Process payroll for all employees for all months
                    for (String empNo : employees.keySet()) {
                        processPayrollForEmployee(empNo);
                    }
                }
            } else if (choice.equals("2")) {
                System.exit(0);
            }
        }
    }

    // ---------------------- PROCESS PAYROLL ----------------------
    static void processPayrollForEmployee(String empNo) {
        List<YearMonth> monthsToProcess = getAllPayrollMonths();
        boolean headerPrinted = false;

        for (YearMonth month : monthsToProcess) {
            payrollMonth = month;
            computePayroll(empNo, headerPrinted);
            headerPrinted = true; // Print header only once
        }
    }

    // ---------------------- GET ALL PAYROLL MONTHS ----------------------
    static List<YearMonth> getAllPayrollMonths() {
        Set<YearMonth> months = new TreeSet<>();
        if (allAttendance.isEmpty()) return new ArrayList<>();

        for (Map<YearMonth, double[]> empData : allAttendance.values()) {
            months.addAll(empData.keySet());
        }
        return new ArrayList<>(months);
    }

    // ---------------------- LOAD ALL ATTENDANCE (SINGLE PASS) ----------------------
    static void loadAllAttendance() throws FileNotFoundException, IOException {
        File file = new File(ATTENDANCE_FILE);
        if (!file.exists())
        throw new FileNotFoundException("Attendance file not found: " + ATTENDANCE_FILE);

        BufferedReader br = new BufferedReader(new FileReader(file));
        String headerLine = readFullCSVLine(br);
        if (headerLine == null) {
            br.close();
            throw new IOException("Attendance file is empty or corrupted: " + ATTENDANCE_FILE);
        }

        DateTimeFormatter df = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        DateTimeFormatter tf = DateTimeFormatter.ofPattern("H:mm");

        String line;
        int lineNumber = 1; // for error messages
        while ((line = readFullCSVLine(br)) != null) { // use robust CSV reader
            lineNumber++;
            String[] d = parseCSVLine(line); // safe splitting
            if (d.length < 6) {
                br.close();
                throw new IOException("Malformed attendance record at line " + lineNumber + ": " + line);
            }

            String empNo = d[0];
            LocalDate date;
            YearMonth ym;
            LocalTime logIn, logOut;

            try {
                date = LocalDate.parse(d[3], df);
                ym = YearMonth.from(date);
            } catch (Exception e) {
                br.close();
                throw new IOException("Invalid date format at line " + lineNumber + ": " + d[3]);
            }

            // Parse times
            try {
            logIn = LocalTime.parse(d[4], tf);
            logOut = LocalTime.parse(d[5], tf);
            } catch (Exception e) {
                br.close();
                throw new IOException("Invalid time format at line " + lineNumber + ": " + d[4] + " / " + d[5]);
            }

            // Skip weekends
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) continue;

             // Adjust logs to official shift times
            if (logIn.isBefore(SHIFT_START)) logIn = SHIFT_START;
            if (logOut.isAfter(SHIFT_END)) logOut = SHIFT_END;

            // Compute work minutes with lunch and max limits
            long workMinutes = Duration.between(logIn, logOut).toMinutes();
            if (workMinutes > 0) workMinutes -= (long)(LUNCH_HOURS * 60);
            if (workMinutes > 480) workMinutes = 480;
            if (workMinutes < 0)  workMinutes = 0;

            // Initialize data structures if needed
            allAttendance.putIfAbsent(empNo, new HashMap<>());
            allAttendance.get(empNo).putIfAbsent(ym, new double[]{0, 0});

            // Accumulate by cutoff
            if (date.getDayOfMonth() <= 15)
                allAttendance.get(empNo).get(ym)[0] += workMinutes;
            else
                allAttendance.get(empNo).get(ym)[1] += workMinutes;
        }
        br.close();
    }

    // ---------------------- LOAD EMPLOYEES ----------------------
    static void loadEmployees() throws FileNotFoundException, IOException {
        File file = new File(EMPLOYEES_FILE);
        if (!file.exists())
        throw new FileNotFoundException("Employee file not found: " + EMPLOYEES_FILE);

        BufferedReader br = new BufferedReader(new FileReader(file));

        // Read header
        String headerLine = readFullCSVLine(br);
        if (headerLine == null) {
            br.close();
            throw new IOException("Employee file is empty or corrupted: " + EMPLOYEES_FILE);
        }
        
        String[] headers = parseCSVLine(headerLine); // safe splitting
        if (headers.length < 2) {
            br.close();
            throw new IOException("Employee file header is invalid: " + EMPLOYEES_FILE);
        }

        // Map headers to column indices
        columnMap.clear(); // clear previous mapping just in case
        for (int i = 0; i < headers.length; i++) {
            columnMap.put(headers[i].toLowerCase().trim(), i);
        }

        // Read employee records
        String line;
        int lineNumber = 1; // header already read
        while ((line = readFullCSVLine(br)) != null) {
            lineNumber++;
            String[] fields = parseCSVLine(line); // safe splitting

            if (fields.length != headers.length) {
                System.err.println("Skipping malformed employee record at line " + lineNumber + ": " + line);
                continue; // skip bad record
            }

            // Use employee number as key
            String empNo = fields[0];
            employees.put(empNo, fields);
        }
        br.close();
    }

    // ---------------------- READ FULL CSV RECORD (handles multi-line fields) ----------------------
    static String readFullCSVLine(BufferedReader br) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        boolean inQuotes = false;

        while ((line = br.readLine()) != null) {
            if (sb.length() > 0) sb.append("\n"); // preserve newlines inside quoted fields
            sb.append(line);

            // Count quotes on this line
            int quoteCount = 0;
            for (char c : line.toCharArray()) {
                if (c == '"') quoteCount++;
            }

             // If odd number of quotes, still inside quoted field
            if (quoteCount % 2 != 0) {
                inQuotes = !inQuotes;
            }

            if (!inQuotes) break; // complete record
        }

        return sb.length() == 0 ? null : sb.toString();
    }

    // ---------------------- PARSE CSV LINE (handles escaped quotes and commas inside quotes) ----------------------
    static String[] parseCSVLine(String line) {
    List<String> fields = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                // handle escaped double quotes ""
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    // Escaped quote inside quoted field
                    field.append('"');
                    i++; // skip next quote
                } else {
                    inQuotes = !inQuotes; // toggle quoting
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(field.toString().trim()); // end of field
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        // Add last field
        fields.add(field.toString().trim());
        return fields.toArray(new String[0]);
    }

    // ---------------------- PRINT EMPLOYEE HEADER ----------------------
    static void printEmployeeHeader(String empNo) {
        String[] e = employees.get(empNo);
        System.out.println("\n===================================================");
        System.out.println("Employee No: " + empNo);
        System.out.println("Employee Name: " + e[columnMap.get("first name")] + " " + e[columnMap.get("last name")]);
        System.out.println("Birthday: " + e[columnMap.get("birthday")]);
        System.out.println("===================================================");
    }

    // ---------------------- COMPUTE PAYROLL (with safe exception handling) ----------------------
    static void computePayroll(String empNo, boolean headerPrinted) {
        String cutoffMonthLabel = payrollMonth.format(DateTimeFormatter.ofPattern("MMMM"));
        int lastDay = payrollMonth.atEndOfMonth().getDayOfMonth();

        String[] e = employees.get(empNo);
        double hourlyRate;

        // Parse hourly rate safely
        try {
            hourlyRate = Double.parseDouble(e[columnMap.get("hourly rate")].replace(",", ""));
        } catch (NumberFormatException ex) {
            System.err.println("Error: Invalid hourly rate for employee " + empNo + ". Skipping payroll.");
            return;
        }
        
        // Retrieve accumulated minutes; divide by 60.0 once per cutoff (single division = no drift).
        double[] totalMinutes = {0, 0};
        if (allAttendance.containsKey(empNo) && allAttendance.get(empNo).containsKey(payrollMonth)) {
            totalMinutes = allAttendance.get(empNo).get(payrollMonth);
        }

        double hoursFirst  = totalMinutes[0] / 60.0;
        double hoursSecond = totalMinutes[1] / 60.0;

        double grossFirst, grossSecond, totalMonthlyGross;
        try {
            grossFirst = hoursFirst * hourlyRate;
            grossSecond = hoursSecond * hourlyRate;
            totalMonthlyGross = grossFirst + grossSecond;
        } catch (ArithmeticException ex) {
            System.err.println("Computation error for employee " + empNo + ": " + ex.getMessage());
            return;
        }

        double sss = 0, philHealth = 0, pagibig = 0, taxWithholding = 0, totalDeductions = 0;
        try {
            sss = computeEmployeeSSS(totalMonthlyGross);
            philHealth = computePhilHealth(totalMonthlyGross);
            pagibig = computePagibig(totalMonthlyGross);
            totalDeductions = sss + philHealth + pagibig;

            double taxable = totalMonthlyGross - totalDeductions;
            taxWithholding = computeTrainTax(taxable);
            totalDeductions += taxWithholding;
        } catch (ArithmeticException ex) {
            System.err.println("Deduction calculation error for employee " + empNo + ": " + ex.getMessage());
            return;
        }

        // Two-net salary logic (intentional)
        double netFirst = grossFirst;              // No deductions for first cutoff
        double netSecond = grossSecond - totalDeductions; // Second cutoff minus deductions

        if (!headerPrinted) printEmployeeHeader(empNo);

        System.out.println("\nCutoff Date: " + cutoffMonthLabel + " 1 to " + cutoffMonthLabel + " 15");
        System.out.println("Hours Worked    : " + String.format("%.2f", hoursFirst));
        System.out.println("Gross Salary    : " + formatAmount(grossFirst));
        System.out.println("Net Salary      : " + formatAmount(netFirst));

        System.out.println("\nCutoff Date: " + cutoffMonthLabel + " 16 to " + cutoffMonthLabel + " " + lastDay);
        System.out.println("Hours Worked       : " + String.format("%.2f", hoursSecond));
        System.out.println("Gross Salary       : " + formatAmount(grossSecond));
        System.out.println("Each Deduction:");
        System.out.println("  - SSS            : " + formatAmount(sss));
        System.out.println("  - PhilHealth     : " + formatAmount(philHealth));
        System.out.println("  - Pag-IBIG       : " + formatAmount(pagibig));
        System.out.println("  - Tax            : " + formatAmount(taxWithholding));
        System.out.println("Total Deductions   : " + formatAmount(totalDeductions));
        System.out.println("Net Salary         : " + formatAmount(netSecond));
        System.out.println("===================================================");
    }

    // ---------------------- FORMAT AMOUNT ----------------------
    static String formatAmount(double amount) {
        return String.format("%,.2f", amount);
    }

    // ---------------------- DEDUCTION METHODS (UPDATED SSS) (with safe exception handling) ----------------------
    static double computeEmployeeSSS(double salary) {
        double[][] sssTable = {
            {24750, 1125.00}, {24250, 1102.50}, {23750, 1080.00}, {23250, 1057.50},
            {22750, 1035.00}, {22250, 1012.50}, {21750, 990.00}, {21250, 967.50},
            {20750, 945.00}, {20250, 922.50}, {19750, 900.00}, {19250, 877.50},
            {18750, 855.00}, {18250, 832.50}, {17750, 810.00}, {17250, 787.50},
            {16750, 765.00}, {16250, 742.50}, {15750, 720.00}, {15250, 697.50},
            {14750, 675.00}, {14250, 652.50}, {13750, 630.00}, {13250, 607.50},
            {12750, 585.00}, {12250, 562.50}, {11750, 540.00}, {11250, 517.50},
            {10750, 495.00}, {10250, 472.50}, {9750, 450.00}, {9250, 427.50},
            {8750, 405.00}, {8250, 382.50}, {7750, 360.00}, {7250, 337.50},
            {6750, 315.00}, {6250, 292.50}, {5750, 270.00}, {5250, 247.50},
            {4750, 225.00}, {4250, 202.50}, {3750, 180.00}, {3250, 157.50}
        };
        try {
            for (double[] bracket : sssTable) {
                if (salary >= bracket[0]) return bracket[1];
            }
            return 135.00; // minimum
        } catch (Exception e) {
            System.err.println("Error computing SSS: " + e.getMessage());
            return 0;
        }
    }

    static double computePhilHealth(double salary) {
        try {
            if (salary < 10000) return 150.00;
            else if (salary <= 60000) return (salary * 0.03) / 2; // employee share only
            else return 900.00; // max 1800, employee share = 900
        } catch (Exception e) {
            System.err.println("Error computing PhilHealth: " + e.getMessage());
            return 0;
        }
    }

    static double computePagibig(double salary){
        try {
        double pagibig = (salary <= 1500) ? salary * 0.01 : salary * 0.02;
        return Math.min(pagibig, 100);
        } catch (Exception e) {
            System.err.println("Error computing Pag-IBIG: " + e.getMessage());
            return 0;
        }
    }

    static double computeTrainTax(double taxable) {
        try {
            if(taxable <= 20832) return 0;
            else if(taxable <= 33332) return (taxable - 20832) * 0.20;
            else if(taxable <= 66666) return 2500 + (taxable - 33333) * 0.25;
            else if(taxable <= 166666) return 10833 + (taxable - 66667) * 0.30;
            else if(taxable <= 666666) return 40833.33 + (taxable - 166667) * 0.32;
            else return 200833.33 + (taxable - 666667) * 0.35;
        } catch (Exception e) {
            System.err.println("Error computing TRAIN tax: " + e.getMessage());
            return 0;
        }
    }
}
