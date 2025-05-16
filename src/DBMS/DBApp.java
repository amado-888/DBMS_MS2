package DBMS;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DBApp {
	static int dataPageSize = 2;
	static Map<String, ArrayList<String[]>> recordsInserted = new HashMap<>();
	static Map<String, Integer> total_pages = new HashMap<>();

	public static void createTable(String tableName, String[] columnsNames) {

		for (int i = 0; i < recordsInserted.size(); i++) {
			String key = tableName + i;
			if (recordsInserted.containsKey(key)) {
				recordsInserted.remove(key);
			}
		}
		if (total_pages.containsKey(tableName)) {
			total_pages.remove(tableName);
		}
		Table t = new Table(tableName, columnsNames);
		FileManager.storeTable(tableName, t);
	}

	public static void updateBitMapIndex(String tableName, String colName, String[] record) {
		BitmapIndex b2 = FileManager.loadTableIndex(tableName, colName);
		Table t = FileManager.loadTable(tableName);
		Map<String, Integer> map = new HashMap<>();
		for (int i = 0; i < t.getColumnsNames().length; i++) {
			map.put(t.getColumnsNames()[i], i);
		}

		int colIndex = map.get(colName);
		for (String val : b2.getB().keySet()) {
			String bitString = b2.getB().get(val);
			if (val.equals(record[colIndex])) {
				bitString += "1";
			} else {
				bitString += "0";
			}
			b2.getB().put(val, bitString); // Save updated bitString
		}

		if (!b2.getB().containsKey(record[colIndex])) {
			String s = "";
			for (int i = 0; i < t.getRecordsCount() - 1; i++) {
				s += "0";
			}
			s += "1";
			b2.getB().put(record[colIndex], s);
		}

		FileManager.storeTableIndex(tableName, colName, b2);
	}

	public static void insert(String tableName, String[] record) {
		Table t = FileManager.loadTable(tableName);
		int page = t.insert(record);
		String key = tableName + page;

		if (recordsInserted.containsKey(key)) {
			recordsInserted.get(key).add(record);
		} else {
			recordsInserted.put(key, new ArrayList<>());
			recordsInserted.get(key).add(record);
		}

		total_pages.put(tableName, page);

		FileManager.storeTable(tableName, t);
		for (int i = 0; i < t.getColumnsNames().length; i++) {
			String columnName = t.getColumnsNames()[i];
			if (FileManager.loadTableIndex(tableName, columnName) != null) {
				updateBitMapIndex(tableName, columnName, record);
			}
		}
	}

	public static ArrayList<String[]> select(String tableName) {
		Table t = FileManager.loadTable(tableName);
		ArrayList<String[]> res = t.select();
		FileManager.storeTable(tableName, t);
		return res;
	}

	public static ArrayList<String[]> select(String tableName, int pageNumber, int recordNumber) {
		Table t = FileManager.loadTable(tableName);
		ArrayList<String[]> res = t.select(pageNumber, recordNumber);
		FileManager.storeTable(tableName, t);
		return res;
	}

	public static ArrayList<String[]> select(String tableName, String[] cols, String[] vals) {
		Table t = FileManager.loadTable(tableName);
		ArrayList<String[]> res = t.select(cols, vals);
		FileManager.storeTable(tableName, t);
		return res;
	}

	public static String getFullTrace(String tableName) {
		Table t = FileManager.loadTable(tableName);
		String res = t.getFullTrace();
		return res;
	}

	public static String getLastTrace(String tableName) {
		Table t = FileManager.loadTable(tableName);
		String res = t.getLastTrace();
		return res;
	}

	public static ArrayList<String[]> validateRecords(String tableName) {
		ArrayList<String[]> res = new ArrayList<>();
		Table t = FileManager.loadTable(tableName);
		int i = 0;
		String key = tableName + i;
		while (recordsInserted.containsKey(key)) {
			Page p = FileManager.loadTablePage(tableName, i);
			if (p == null) {
				res.addAll(recordsInserted.get(key));
			}
			i++;
			key = tableName + i;

		}
		t.getTrace().add("Validating records: " + res.size() + " records missing.");
		FileManager.storeTable(tableName, t);

		return res;
	}

	public static void insertIntopage(String tableName, ArrayList<String[]> missing, int page, int recovered) {
		Page p = new Page();
		int i = 0;
		while (i < dataPageSize && !missing.isEmpty()) {
			recovered++;
			p.insert(missing.remove(0));
			i++;
		}
		FileManager.storeTablePage(tableName, page, p);

	}

	public static void recoverRecords(String tableName, ArrayList<String[]> missing) {
		;
		int size = total_pages.get(tableName) + 1;
		ArrayList<Integer> pages = new ArrayList<>();
		int i = 0;
		int countRec = 0;
		while (!missing.isEmpty() && i < size) {
			Page page = FileManager.loadTablePage(tableName, i);
			if (page == null) {
				pages.add(i);
				insertIntopage(tableName, missing, i, countRec);

			}
			i++;

		}
		Table t = FileManager.loadTable(tableName);
		t.getTrace().add("Recovering " + countRec + " records" + " in pages: " + pages.toString());
		FileManager.storeTable(tableName, t);
	}

	public static void createBitMapIndex(String tableName, String colName) {
		String bitIndex = "";
		long startTime = System.currentTimeMillis();
		BitmapIndex b = new BitmapIndex(tableName, colName);
		Table t = FileManager.loadTable(tableName);
		Map<String, Integer> map = new HashMap<>();
		for (int i = 0; i < t.getColumnsNames().length; i++) {
			map.put(t.getColumnsNames()[i], i);
		}

		int colIndex = map.get(colName);

		for (int i = 0; i < t.getPageCount(); i++) {
			Page p = FileManager.loadTablePage(tableName, i);
			for (int j = 0; j < p.getRecords().size(); j++) {
				String columnVal = p.getRecords().get(j)[colIndex];
				if (!b.getB().containsKey(columnVal)) {
					b.getB().put(columnVal, bitIndex);
				}

			}
		}

		for (int i = 0; i < t.getPageCount(); i++) {
			Page p = FileManager.loadTablePage(tableName, i);
			for (int j = 0; j < p.getRecords().size(); j++) {
				String currentVal = p.getRecords().get(j)[colIndex];

				// For each unique value in the bitmap, append 1 if match, 0 otherwise
				for (String val : b.getB().keySet()) {
					String bitString = b.getB().get(val);
					if (val.equals(currentVal)) {
						bitString += "1";
					} else {
						bitString += "0";
					}
					b.getB().put(val, bitString); // Save updated bitString
				}
			}
		}

		FileManager.storeTableIndex(tableName, colName, b);
		long endTime = System.currentTimeMillis();
		long exc = endTime - startTime;
		t.getTrace().add("Index created for column: " + colName + ", execution time (mil):" + exc);
		FileManager.storeTable(tableName, t);
	}

	public static String getValueBits(String tableName, String colName, String value) {
		BitmapIndex b = FileManager.loadTableIndex(tableName, colName);
		String valueBits = b.getB().get(value);
		if (valueBits == null) {
			Table t = FileManager.loadTable(tableName);
			String temp = "";
			for (int i = 0; i < t.getRecordsCount(); i++) {
				temp += "0";
			}
			valueBits = temp;
		}
		return valueBits;
	}

	public static ArrayList<String[]> selectFullyIndexed(String tableName, String[] cols, String[] vals,
			ArrayList<String> IndexedColumns) {
		ArrayList<String[]> res = new ArrayList<>();
		Long startTime = System.currentTimeMillis();
		BitmapIndex b = FileManager.loadTableIndex(tableName, cols[0]);
		Table t = FileManager.loadTable(tableName);
		BigInteger index = new BigInteger(b.getB().get(vals[0]), 2);
		for (int i = 1; i < IndexedColumns.size(); i++) {
			b = FileManager.loadTableIndex(tableName, cols[i]);
			BigInteger temp = new BigInteger(b.getB().get(vals[i]), 2);

			index = index.and(temp);
		}
		String finalRecords = index.toString(2);
		if (finalRecords.length() != t.getRecordsCount()) {
			int diff = (t.getRecordsCount() - finalRecords.length());
			for (int i = 1; i <= diff; i++) {
				finalRecords = "0" + finalRecords;
			}
		}

		for (int i = 0; i < finalRecords.length(); i++) {
			if (finalRecords.charAt(i) == '1') {
				int recordNum = i + 1; // 1-based record number
				int pagenum = (recordNum - 1) / dataPageSize;
				int recordInPage = (recordNum - 1) % dataPageSize;
				Page p = FileManager.loadTablePage(tableName, (int) pagenum);
				res.add(p.getRecords().get(recordInPage));

			} else {
				continue;
			}
		}
		Long endTime = System.currentTimeMillis();
		Arrays.sort(cols);
		t.getTrace()
				.add("Select index condition:" + Arrays.toString(cols) + "->" + Arrays.toString(vals)
						+ ", Indexed columns:" + Arrays.toString(cols) + ", Indexed selection count:" + index.bitCount()
						+ ", Final count: "
						+ index.bitCount() +
						", execution time (mil):" + (endTime - startTime));
		FileManager.storeTable(tableName, t);

		return res;
	}

	public static ArrayList<String[]> selectNonIndexed(String tableName, String[] cols, String[] vals) {
		Long startTime = System.currentTimeMillis();
		int finalCount = 0;
		Table t = FileManager.loadTable(tableName);
		ArrayList<String[]> res = new ArrayList<>();
		Map<String, Integer> y = new HashMap<>();
		Map<String, String> x = new HashMap<>();
		for (int i = 0; i < t.getColumnsNames().length; i++) {
			y.put(t.getColumnsNames()[i], i);
		}
		for (int i = 0; i < cols.length; i++) {
			x.put(cols[i], vals[i]);
		}

		for (int i = 0; i < t.getPageCount(); i++) {
			Page p = FileManager.loadTablePage(tableName, i);
			for (int j = 0; j < p.getRecords().size(); j++) {
				Boolean flag = true;
				for (int k = 0; k < cols.length; k++) {
					int colIndex2 = y.get(cols[k]);
					if (!p.getRecords().get(j)[colIndex2].equals(x.get(cols[k]))) {
						flag = false;
						break;
					}
				}
				if (flag) {
					res.add(p.getRecords().get(j));
					finalCount++;
				}
			}
		}
		Long endTime = System.currentTimeMillis();
		Arrays.sort(cols);
		t.getTrace()
				.add("Select index condition:" + Arrays.toString(cols) + "->" + Arrays.toString(vals)
						+ ", Non Indexed:" + Arrays.toString(cols) + ", Final count: "
						+ finalCount +
						", execution time (mil):" + (endTime - startTime));
		FileManager.storeTable(tableName, t);

		return res;
	}

	public static ArrayList<String[]> selectSomeIndexed(String tableName, String[] cols, String[] vals,
			ArrayList<String> IndexedColumns, ArrayList<String> NonIndexedColumns) {
		Long startTime = System.currentTimeMillis();
		Table t = FileManager.loadTable(tableName);
		ArrayList<String[]> res1 = new ArrayList<>();
		ArrayList<String[]> res = new ArrayList<>();
		Map<String, Integer> y = new HashMap<>();
		Map<String, String> x = new HashMap<>();
		for (int i = 0; i < t.getColumnsNames().length; i++) {
			y.put(t.getColumnsNames()[i], i);
		}
		for (int j = 0; j < cols.length; j++) {
			x.put(cols[j], vals[j]);
		}
		// Indexed part
		BigInteger index = new BigInteger(
				getValueBits(tableName, IndexedColumns.get(0), x.get(IndexedColumns.get(0))), 2);
		for (int i = 1; i < IndexedColumns.size(); i++) {
			BigInteger temp = new BigInteger(
					getValueBits(tableName, IndexedColumns.get(i), x.get(IndexedColumns.get(i))), 2);
			index = index.and(temp);
		}
		String s = index.toString(2);
		if (s.length() != t.getRecordsCount()) {
			int diff = (t.getRecordsCount() - s.length());
			for (int i = 1; i <= diff; i++) {
				s = "0" + s;
			}
		}
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) == '1') {
				int recordNum = i + 1; // 1-based record number
				int pagenum = (recordNum - 1) / dataPageSize;
				int recordInPage = (recordNum - 1) % dataPageSize;
				Page p = FileManager.loadTablePage(tableName, (int) pagenum);

				res1.add(p.getRecords().get(recordInPage));

			} else {
				continue;
			}
		}
		// Final Part
		int finalCount = 0;
		for (int i = 0; i < res1.size(); i++) {
			Boolean flag = true;
			for (int j = 0; j < NonIndexedColumns.size(); j++) {
				int colIndex = y.get(NonIndexedColumns.get(j));
				if (!res1.get(i)[colIndex].equals(x.get(NonIndexedColumns.get(j)))) {
					flag = false;
					break;
				}
			}
			if (flag) {
				res.add(res1.get(i));
				finalCount++;
			}
		}
		Long endTime = System.currentTimeMillis();
		IndexedColumns.sort(null);
		NonIndexedColumns.sort(null);
		t.getTrace()
				.add("Select index condition:" + Arrays.toString(cols) + "->" + Arrays.toString(vals)
						+ ", Indexed columns:" + IndexedColumns.toString() + ", Indexed selection count:"
						+ index.bitCount()
						+ ", Non Indexed: " + NonIndexedColumns.toString() + ", Final count: "
						+ finalCount +
						", execution time (mil):" + (endTime - startTime));
		FileManager.storeTable(tableName, t);
		return res;

	}

	public static ArrayList<String[]> selectIndex(String tableName, String[] cols, String[] vals) {
		ArrayList<String[]> res = new ArrayList<>();
		ArrayList<String> IndexedColumns = new ArrayList<>();
		ArrayList<String> NonIndexedColunms = new ArrayList<>();
		BitmapIndex b;

		for (int i = 0; i < cols.length; i++) {
			b = FileManager.loadTableIndex(tableName, cols[i]);
			if (b != null) {
				IndexedColumns.add(cols[i]);
			} else {
				NonIndexedColunms.add(cols[i]);
			}
		}
		if (IndexedColumns.size() == cols.length) {
			res = selectFullyIndexed(tableName, cols, vals, IndexedColumns);

		} else if (IndexedColumns.size() >= 1 && IndexedColumns.size() < cols.length) {
			res = selectSomeIndexed(tableName, cols, vals, IndexedColumns, NonIndexedColunms);
		} else {
			res = selectNonIndexed(tableName, cols, vals);
		}
		return res;
	}

	public static void main(String[] args) throws IOException {
		FileManager.reset();
		String[] cols = { "id", "name", "major", "semester", "gpa" };
		createTable("student", cols);
		String[] r1 = { "1", "stud1", "CS", "5", "0.9" };
		insert("student", r1);
		String[] r2 = { "2", "stud2", "BI", "7", "1.2" };
		insert("student", r2);
		String[] r3 = { "3", "stud3", "CS", "2", "2.4" };
		insert("student", r3);
		createBitMapIndex("student", "gpa");
		createBitMapIndex("student", "major");
		System.out.println("Bitmap of the value of CS from the major index:" + getValueBits("student", "major", "CS"));
		System.out.println("Bitmap of the value of 1.2 from the gpa index:" + getValueBits("student", "gpa", "1.2"));
		String[] r4 = { "4", "stud4", "CS", "9", "1.2" };
		insert("student", r4);
		String[] r5 = { "5", "stud5", "BI", "4", "3.5" };
		insert("student", r5);
		System.out.println("After new insertions:");
		System.out.println("Bitmap of the value of CS from the major index:" + getValueBits("student", "major", "CS"));
		System.out.println("Bitmap of the value of 1.2 from the gpa index:" + getValueBits("student", "gpa", "1.2"));
		System.out.println("Output of selection using index when all columns of the select conditions are indexed:");
		ArrayList<String[]> result1 = selectIndex("student", new String[] { "major",
				"gpa" },
				new String[] { "CS", "1.2" });
		for (String[] array : result1) {
			for (String str : array) {
				System.out.print(str + " ");
			}
			System.out.println();
		}
		System.out.println("Last trace of the table: " + getLastTrace("student"));
		System.out.println("--------------------------------");

		System.out.println(
				"Output of selection using index when only one column of the columns of the select conditions are indexed:");
		ArrayList<String[]> result2 = selectIndex("student", new String[] { "major",
				"semester" },
				new String[] { "CS", "5" });
		for (String[] array : result2) {
			for (String str : array) {
				System.out.print(str + " ");
			}
			System.out.println();
		}
		System.out.println("Last trace of the table: " + getLastTrace("student"));
		System.out.println("--------------------------------");

		System.out.println(
				"Output of selection using index when some of the columns of the select conditions are indexed:");
		ArrayList<String[]> result3 = selectIndex("student", new String[] { "major",
				"semester", "gpa" },
				new String[] { "CS", "5", "0.9" });
		for (String[] array : result3) {
			for (String str : array) {
				System.out.print(str + " ");
			}
			System.out.println();
		}
		System.out.println("Last trace of the table: " + getLastTrace("student"));
		System.out.println("--------------------------------");

		System.out.println("Full Trace of the table:");
		System.out.println(getFullTrace("student"));
		System.out.println("--------------------------------");
		System.out.println("The trace of the Tables Folder:");
		System.out.println(FileManager.trace());
	}

}
