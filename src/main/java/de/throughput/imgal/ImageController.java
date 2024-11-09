// src/main/java/de/throughput/imgal/ImageController.java
package de.throughput.imgal;

import org.apache.commons.imaging.bytesource.ByteSource;
import org.apache.commons.imaging.formats.jpeg.JpegImageParser;
import org.apache.xmpbox.xml.DomXmpParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
public class ImageController {

    @Value("${image.path}")
    private File imageDirectory;

    @GetMapping("/i/{id}")
    public String displayImage(@PathVariable String id, Model model) {
        // Validate that 'id' contains only digits
        if (!id.matches("\\d+")) {
            model.addAttribute("errorMessage", "non-numeric id: '%s'".formatted(id));
            return "error";
        }

        // Construct the image path
        String imageFileName = String.format("i%s.jpg", id);
        File imagePath = new File(imageDirectory, imageFileName);

        if (!imagePath.canRead()) {
            model.addAttribute("errorMessage", "can't read image path: '%s'".formatted(imagePath.getAbsolutePath()));
            return "error";
        }

        getImageDescription(imagePath, imageFileName, model);

        // Get the list of IDs and determine previous and next IDs
        List<Long> idList = getSortedIdList();
        long currentId = Long.parseLong(id);
        Long previousId = null;
        Long nextId = null;

        // Find the index of the current ID
        int index = idList.indexOf(currentId);
        if (index != -1) {
            if (index > 0) {
                previousId = idList.get(index - 1);
            }
            if (index < idList.size() - 1) {
                nextId = idList.get(index + 1);
            }
        }

        model.addAttribute("imagePath", String.format("/image/%s", imageFileName));
        if (previousId != null) {
            model.addAttribute("previousId", previousId.toString());
        }
        if (nextId != null) {
            model.addAttribute("nextId", nextId.toString());
        }
        return "image";
    }

    private static void getImageDescription(File imagePath, String imageFileName, Model model) {
        try (InputStream is = new FileInputStream(imagePath)) {
            // Use JpegImageParser to get XMP XML data
            var parser = new JpegImageParser();
            String xmpXml = parser.getXmpXml(ByteSource.inputStream(is, imageFileName), null);
            if (xmpXml != null) {
                var metadata = new DomXmpParser().parse(xmpXml.getBytes(StandardCharsets.UTF_8));
                var dc = metadata.getDublinCoreSchema();
                if (dc != null) {
                    model.addAttribute("imagePrompt", dc.getDescription());
                    model.addAttribute("originalPrompt", dc.getSource());
                }
            }
        } catch (Exception e) {
            model.addAttribute("errorMessage", e.getMessage());
        }
    }

    private List<Long> getSortedIdList() {
        File[] files = imageDirectory.listFiles();
        if (files == null) {
            return Collections.emptyList();
        }

        List<Long> idList = new ArrayList<>();
        Pattern pattern = Pattern.compile("i(\\d+)\\.jpg");

        for (File file : files) {
            String name = file.getName();
            Matcher matcher = pattern.matcher(name);
            if (matcher.matches()) {
                String idStr = matcher.group(1);
                long fileId = Long.parseLong(idStr);
                idList.add(fileId);
            }
        }

        // Sort the list
        Collections.sort(idList);

        return idList;
    }
}
