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

    static Scanner sc = new Scanner(System.in);
    static Map<String, String[]> employees = new HashMap<>();
    static Map<String, double[]> attendance = new HashMap<>();
    static Map<String, Integer> columnMap = new HashMap<>();
    static String currentUser;
    static YearMonth payrollMonth;

    static String formatAmount(double amount) {
        return String.format("%,.2f", amount);
    }

    public static void main(String[] args) throws Exception {
        loadEmployees();
        login();

        if (currentUser.equals("employee"))
            employeeMenu();
        else
            payrollStaffMenu();
    }

    static void login() {
        System.out.println("------- MOTORPH PAYROLL SYSTEM -------");
        System.out.print("Username: ");
        String user = sc.nextLine();
        System.out.print("Password: ");
        String pass = sc.nextLine();

        if ((user.equals("employee") || user.equals("payroll_staff")) && pass.equals("12345"))
            currentUser = user;
        else {
            System.out.println("Incorrect username and/or password.");
            System.exit(0);
        }
    }

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

            String[] e = employees.get(empNo);
            System.out.println("\n===================================================");
            System.out.println("Employee No: " + empNo);
            System.out.println("Employee Name: " + e[columnMap.get("first name")] + " " + e[columnMap.get("last name")]);
            System.out.println("Birthday: " + e[columnMap.get("birthday")]);
            System.out.println("===================================================");

        } else if (choice.equals("2")) {
            System.exit(0);
        }
    }

    static void payrollStaffMenu() throws Exception {
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

                    List<YearMonth> monthsToProcess = getAllPayrollMonths();
                    boolean headerPrinted = false;

                    for (YearMonth month : monthsToProcess) {
                        payrollMonth = month;
                        attendance.clear();
                        loadAttendance();
                        computePayroll(empNo, headerPrinted);
                        headerPrinted = true;
                    }

                } else if (subChoice.equals("2")) {
                    List<YearMonth> monthsToProcess = getAllPayrollMonths();
                    for (String empNo : employees.keySet()) {
                        boolean headerPrinted = false;
                        for (YearMonth month : monthsToProcess) {
                            payrollMonth = month;
                            attendance.clear();
                            loadAttendance();
                            computePayroll(empNo, headerPrinted);
                            headerPrinted = true;
                        }
                    }
                }
            } else if (choice.equals("2")) {
                System.exit(0);
            }
        }
    }

    static List<YearMonth> getAllPayrollMonths() throws Exception {
        Set<YearMonth> months = new TreeSet<>();
        File file = new File("attendance.csv");
        if(!file.exists()) return new ArrayList<>();

        BufferedReader br = new BufferedReader(new FileReader(file));
        br.readLine(); 
        DateTimeFormatter df = DateTimeFormatter.ofPattern("MM/dd/yyyy");

        String line;
        while ((line = br.readLine()) != null) {
            String[] d = line.split(",");
            if (d.length < 4) continue;
            try {
                LocalDate date = LocalDate.parse(d[3], df);
                months.add(YearMonth.from(date));
            } catch (Exception ignored) {}
        }
        br.close();
        return new ArrayList<>(months);
    }

    static void loadAttendance() throws Exception {
        BufferedReader br = new BufferedReader(new FileReader("attendance.csv"));
        br.readLine(); 

        DateTimeFormatter df = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        DateTimeFormatter tf = DateTimeFormatter.ofPattern("H:mm");

        String line;
        while ((line = br.readLine()) != null) {
            String[] d = line.split(",");
            String empNo = d[0];
            LocalDate date = LocalDate.parse(d[3], df);

            if (!YearMonth.from(date).equals(payrollMonth)) continue;
            if(date.getDayOfWeek()==DayOfWeek.SATURDAY||date.getDayOfWeek()==DayOfWeek.SUNDAY) continue;

            LocalTime logIn = LocalTime.parse(d[4], tf);
            LocalTime logOut = LocalTime.parse(d[5], tf);

            LocalTime shiftStart = LocalTime.of(8,0);
            LocalTime shiftEnd = LocalTime.of(17,0);

            if (logOut.isAfter(shiftEnd)) logOut = shiftEnd;

            double workHours = Duration.between(logIn, logOut).toMinutes() / 60.0 - 1.0;
            if (workHours > 8) workHours = 8;
            
            if(logIn.isAfter(shiftStart.plusMinutes(10))){
                double lateHours = Duration.between(shiftStart, logIn).toMinutes()/60.0;
                workHours -= lateHours;
            }
            if(workHours < 0) workHours = 0;

            attendance.putIfAbsent(empNo, new double[]{0, 0});
            if (date.getDayOfMonth() <= 15)
                attendance.get(empNo)[0] += workHours;
            else
                attendance.get(empNo)[1] += workHours;
        }
        br.close();
    }

    static void loadEmployees() throws Exception {
        BufferedReader br = new BufferedReader(new FileReader("employees.csv"));
        String[] headers = br.readLine().split(",");
        for (int i = 0; i < headers.length; i++)
            columnMap.put(headers[i].toLowerCase(), i);

        String line;
        while ((line = br.readLine()) != null) {
            List<String> fields = new ArrayList<>();
            boolean inQuotes = false;
            StringBuilder field = new StringBuilder();
            for (char c : line.toCharArray()) {
                if (c == '"') inQuotes = !inQuotes;
                else if (c == ',' && !inQuotes) {
                    fields.add(field.toString().replaceAll("^\"|\"$", ""));
                    field = new StringBuilder();
                } else field.append(c);
            }
            fields.add(field.toString().replaceAll("^\"|\"$", ""));
            String[] data = fields.toArray(new String[0]);
            employees.put(data[0], data);
        }
        br.close();
    }

    static void computePayroll(String empNo, boolean headerPrinted) {
        String cutoffMonthLabel = payrollMonth.format(DateTimeFormatter.ofPattern("MMMM"));
        int lastDay = payrollMonth.atEndOfMonth().getDayOfMonth();

        String[] e = employees.get(empNo);
        double hourlyRate = Double.parseDouble(e[columnMap.get("hourly rate")].replace(",", ""));
        double[] hours = attendance.getOrDefault(empNo, new double[]{0, 0});

        double grossFirst = hours[0] * hourlyRate;
        double grossSecond = hours[1] * hourlyRate;
        double totalMonthlyGross = grossFirst + grossSecond;

        // Deduction Computation
        double sss = computeEmployeeSSS(totalMonthlyGross);
        double philHealth = computePhilHealth(totalMonthlyGross);
        double pagibig = computePagibig(totalMonthlyGross);
        double totalContribution = sss + philHealth + pagibig;
        
        double taxable = totalMonthlyGross - totalContribution;
        double taxWithholding = computeTrainTax(taxable);
        double totalDeductions = totalContribution + taxWithholding;
        
        double netFirst = grossFirst;
        double netSecond = grossSecond - totalDeductions;

        if (!headerPrinted) {
            System.out.println("\n===================================================");
            System.out.println("Employee No: " + empNo);
            System.out.println("Employee Name: " + e[columnMap.get("first name")] + " " + e[columnMap.get("last name")]);
            System.out.println("Birthday: " + e[columnMap.get("birthday")]);
            System.out.println("===================================================");
        }

        // Cutoff 1
        System.out.println("\nCutoff Date: " + cutoffMonthLabel + " 1 to " + cutoffMonthLabel + " 15");
        System.out.println("Hours Worked    : " + String.format("%.2f", hours[0]));
        System.out.println("Gross Salary    : " + formatAmount(grossFirst));
        System.out.println("Net Salary      : " + formatAmount(netFirst));

        // Cutoff 2 - Matches your spacing exactly
        System.out.println("\nCutoff Date: " + cutoffMonthLabel + " 16 to  " + cutoffMonthLabel + " " + lastDay);
        System.out.println("Hours Worked       : " + String.format("%.2f", hours[1]));
        System.out.println("Gross Salary       : " + formatAmount(grossSecond));
        System.out.println("Each Deduction:");
        System.out.println("  - SSS            : " + formatAmount(sss));
        System.out.println("  - PhilHealth     : " + formatAmount(philHealth));
        System.out.println("  - Pag-IBIG       : " + formatAmount(pagibig));
        System.out.println("  - Tax            : " + formatAmount(taxWithholding));
        System.out.println("Total Deductions   : " + formatAmount(totalDeductions));
        System.out.println("Net Salary            : " + formatAmount(netSecond));
        System.out.println("===================================================");
    }


    //  ---------- CONTRIBUTIONS & DEDUCTIONS ----------
    
    // ---------- COMPUTE SSS CONTRIBUTION ----------
    // based on MotorPH's SSS table
    
    static double computeEmployeeSSS(double salary) {
        if (salary < 3250) return 135.00;
        else if (salary < 3750) return 157.50;
        else if (salary < 4250) return 180.00;
        else if (salary < 4750) return 202.50;
        else if (salary < 5250) return 225.00;
        else if (salary < 5750) return 247.50;
        else if (salary < 6250) return 270.00;
        else if (salary < 6750) return 292.50;
        else if (salary < 7250) return 315.00;
        else if (salary < 7750) return 337.50;
        else if (salary < 8250) return 360.00;
        else if (salary < 8750) return 382.50;
        else if (salary < 9250) return 405.00;
        else if (salary < 9750) return 427.50;
        else if (salary < 10250) return 450.00;
        else if (salary < 10750) return 472.50;
        else if (salary < 11250) return 495.00;
        else if (salary < 11750) return 517.50;
        else if (salary < 12250) return 540.00;
        else if (salary < 12750) return 562.50;
        else if (salary < 13250) return 585.00;
        else if (salary < 13750) return 607.50;
        else if (salary < 14250) return 630.00;
        else if (salary < 14750) return 652.50;
        else if (salary < 15250) return 675.00;
        else if (salary < 15750) return 697.50;
        else if (salary < 16250) return 720.00;
        else if (salary < 16750) return 742.50;
        else if (salary < 17250) return 765.00;
        else if (salary < 17750) return 787.50;
        else if (salary < 18250) return 810.00;
        else if (salary < 18750) return 832.50;
        else if (salary < 19250) return 855.00;
        else if (salary < 19750) return 877.50;
        else if (salary < 20250) return 900.00;
        else if (salary < 20750) return 922.50;
        else if (salary < 21250) return 945.00;
        else if (salary < 21750) return 967.50;
        else if (salary < 22250) return 990.00;
        else if (salary < 22750) return 1012.50;
        else if (salary < 23250) return 1035.00;
        else if (salary < 23750) return 1057.50;
        else if (salary < 24250) return 1080.00;
        else if (salary < 24750) return 1102.50;
        else return 1125.00;
    }


    // ---------- COMPUTE PHILHEALTH CONTRIBUTION ----------
    // based on MotorPH's PhilHealth table
    
    static double computePhilHealth(double salary){
        if(salary <= 60000){
            return salary * 0.015; // 50% of 3%
        } else {
            return 900; // 50% of maximum 1800
        }
    }

    // ---------- COMPUTE PAGIBIG CONTRIBUTION ----------
    // based on MotorPH's PAGIBIG table

    static double computePagibig(double salary){
        double pagibig;
        if (salary <= 1500){
            pagibig = salary * 0.01; // employee's contribution only
        }
        else {
            pagibig = salary * 0.02;
        }
        if (pagibig > 100) pagibig = 100; // maximum contribution amount

        return pagibig;
    }

    // ---------- COMPUTE TAX DEDUCTION ----------
    // based on MotorPH's Tax table
    
    static double computeTrainTax(double taxable) {
        if(taxable <= 20832) return 0;
        else if(taxable <= 33332) return (taxable - 20832) * 0.20;
        else if(taxable <= 66666) return 2500 + (taxable - 33333) * 0.25;
        else if(taxable <= 166666) return 10833 + (taxable - 66667) * 0.30;
        else if(taxable <= 666666) return 40833.33 + (taxable - 166667) * 0.32;
        else return 200833.33 + (taxable - 666667) * 0.35;
    }
}

