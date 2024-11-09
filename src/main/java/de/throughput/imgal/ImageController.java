// src/main/java/de/throughput/imgal/ImageController.java
package de.throughput.imgal;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.imaging.bytesource.ByteSource;
import org.apache.commons.imaging.formats.jpeg.JpegImageParser;
import org.apache.xmpbox.xml.DomXmpParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

@Controller
public class ImageController {

    public static final int TUMBNAIL_HEIGHT = 128;

    @Value("${image.path}")
    private File imageDirectory;
    @Value("${thumbnail.path}")
    private File thumbnailDirectory;

    @GetMapping("/i/{id}")
    public String displayImage(@PathVariable String id, Model model) {
        // Validate that 'id' contains only digits
        if (!id.matches("\\d+")) {
            model.addAttribute("errorMessage", "Non-numeric id: '%s'".formatted(id));
            return "error";
        }

        // Construct the image path
        String imageFileName = String.format("i%s.jpg", id);
        File imagePath = new File(imageDirectory, imageFileName);

        if (!imagePath.canRead()) {
            model.addAttribute("errorMessage", "Can't read image path: '%s'".formatted(imagePath.getAbsolutePath()));
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

            // Calculate the page number for the current image
            int imagesPerPage = 25;
            int currentPage = index / imagesPerPage; // Zero-based page index

            model.addAttribute("currentPage", currentPage);
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



    @GetMapping("/t/{id}.jpg")
    public void renderThumbnail(@PathVariable String id, HttpServletResponse response) {
        // Validate that 'id' contains only digits
        if (!id.matches("\\d+")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        String thumbnailFileName = String.format("i%s.jpg", id);
        File thumbnailFile = new File(thumbnailDirectory, thumbnailFileName);

        try {
            // If the thumbnail already exists, serve it
            if (thumbnailFile.exists() && thumbnailFile.canRead()) {
                response.setContentType("image/jpeg");
                try (InputStream is = new FileInputStream(thumbnailFile)) {
                    is.transferTo(response.getOutputStream());
                }
                return;
            }

            // Load the original image
            String imageFileName = String.format("i%s.jpg", id);
            File imageFile = new File(imageDirectory, imageFileName);
            if (!imageFile.exists() || !imageFile.canRead()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            BufferedImage originalImage = ImageIO.read(imageFile);
            if (originalImage == null) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            }

            // Calculate the thumbnail dimensions to keep the aspect ratio
            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();
            int newHeight = TUMBNAIL_HEIGHT;
            int newWidth = (originalWidth * newHeight) / originalHeight;

            // Create and scale the thumbnail image
            BufferedImage thumbnailImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = thumbnailImage.createGraphics();
            graphics.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
            graphics.dispose();

            // Save the thumbnail
            ImageIO.write(thumbnailImage, "jpg", thumbnailFile);

            // Serve the thumbnail
            response.setContentType("image/jpeg");
            try (InputStream is = new FileInputStream(thumbnailFile)) {
                is.transferTo(response.getOutputStream());
            }

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/")
    public String showIndex(@RequestParam(value = "page", defaultValue = "0") int page, Model model) {
        List<Long> idList = getSortedIdList();

        int imagesPerPage = 25;
        int totalPages = (int) Math.ceil((double) idList.size() / imagesPerPage);

        // Ensure the page number is within bounds
        if (page < 0) {
            page = 0;
        } else if (page >= totalPages) {
            page = totalPages - 1;
        }

        int startIndex = page * imagesPerPage;
        int endIndex = Math.min(startIndex + imagesPerPage, idList.size());
        List<Long> pageIdList = idList.subList(startIndex, endIndex);

        // Divide pageIdList into rows of 5 elements
        List<List<Long>> rows = new ArrayList<>();
        for (int i = 0; i < pageIdList.size(); i += 5) {
            rows.add(pageIdList.subList(i, Math.min(i + 5, pageIdList.size())));
        }

        // Determine if previous or next pages are available
        Integer previousPage = (page > 0) ? page - 1 : null;
        Integer nextPage = (page < totalPages - 1) ? page + 1 : null;

        // Calculate page numbers to display in pagination
        int paginationDisplaySize = 5; // Number of page links to display
        int startPage = Math.max(0, page - paginationDisplaySize / 2);
        int endPage = Math.min(totalPages - 1, startPage + paginationDisplaySize - 1);

        // Adjust startPage if we are near the end
        if (endPage - startPage < paginationDisplaySize - 1) {
            startPage = Math.max(0, endPage - paginationDisplaySize + 1);
        }

        List<Integer> pageNumbers = new ArrayList<>();
        for (int i = startPage; i <= endPage; i++) {
            pageNumbers.add(i);
        }

        model.addAttribute("rows", rows);
        model.addAttribute("previousPage", previousPage);
        model.addAttribute("nextPage", nextPage);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageNumbers", pageNumbers);

        return "index";
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
