package com.empatica.sample;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;

/**
 * Created by Maxim on 20-09-16.
 */
public class FileHandler {
    private File mFile;
    private FileWriter fileWriter;
    private int mLineCounter = 1;
    private static final boolean appendToFile = Boolean.TRUE;

    public FileHandler(String filename, Context context) {
        File filesDir = context.getFilesDir();
        mFile = new File(filesDir, filename);

        overwriteFile( mFile );
    }

    public void overwriteFile(File file) {
        if ( file.exists() ) {
            file.delete();
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void write(String string) {
        try {
            fileWriter = new FileWriter(mFile, appendToFile);
            fileWriter.write(string);
            fileWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeRow(String string) {
        // Writes the string on a new line
        try {
            fileWriter = new FileWriter(mFile, appendToFile);
            // Write newline
            fileWriter.write(System.lineSeparator());
            // Write data
            fileWriter.write(string);
            fileWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeCsvRow(Object[] elements) {
        // Also adds a line number
        String row = "" + mLineCounter + ": ";
        mLineCounter += 1;

        // Add elements to the row. Tab separated
        for (Object element: elements)
        {
            String strElement = element.toString();
            row += strElement + "\t";
        }
        writeRow(row);
//        System.out.println(row);
    }

    public void writeMeasurement(double timestamp, Object... elements) {
        // Initialize row with a line number and timestamp
        String row = String.format("%d: %.3f", mLineCounter, timestamp);
        mLineCounter += 1;

        // Add elements to the row. Tab separated
        for (Object element: elements)
        {
            String strElement = element.toString();
            row += "\t" + strElement;
        }
        writeRow(row);
    }

    public String read() {
        String result = "";
        try {
            Scanner input = new Scanner(mFile);
            int lineNumber = 1;
            while (input.hasNextLine()) {
                String line = input.nextLine();
                result = result + lineNumber + " :" + line + "\n";
                lineNumber++;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return (result);
    }
}
