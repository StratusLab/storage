/*
 Created as part of the StratusLab project (http://stratuslab.eu),
 co-funded by the European Commission under the Grant Agreement
 INSFO-RI-261552.

 Copyright (c) 2011, Centre National de la Recherche Scientifique (CNRS)

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package eu.stratuslab.storage.disk.resources;

import static org.restlet.data.MediaType.APPLICATION_JSON;
import static org.restlet.data.MediaType.TEXT_HTML;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.restlet.data.Form;
import org.restlet.data.Status;
import org.restlet.ext.fileupload.RestletFileUpload;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;

import eu.stratuslab.storage.disk.main.ServiceConfiguration;
import eu.stratuslab.storage.disk.utils.DiskUtils;
import eu.stratuslab.storage.disk.utils.FileUtils;
import eu.stratuslab.storage.persistence.Disk;
import eu.stratuslab.storage.persistence.DiskView;

public class DisksResource extends DiskBaseResource {

	private Form form = null;

	@Get("html")
	public Representation getAsHtml() {

		getLogger().info("DisksResource getAsHtml");

		Map<String, Object> info = listDisks();

		return createTemplateRepresentation("html/disks.ftl", info, TEXT_HTML);
	}

	@Get("json")
	public Representation getAsJson() {

		getLogger().info("DisksResource getAsJson");

		Map<String, Object> info = listDisks();

		return createTemplateRepresentation("json/disks.ftl", info,
				APPLICATION_JSON);

	}

	@Post("form:html")
	public Representation createDiskRequestFromHtml(Representation entity) {

		Disk disk = validateAndCreateDisk();

		redirectSeeOther(getBaseUrl() + "/disks/" + disk.getUuid());

		return null;
	}

	protected Disk validateAndCreateDisk() {
		form = new Form(getRequestEntity());

		Disk disk = getDisk(form);

		validateNewDisk(disk);

		createDisk(disk);
		
		return disk;
	}

	@Post("form:json")
	public Representation createDiskRequestFromJson(Representation entity) {

		Disk disk = validateAndCreateDisk();

		setStatus(Status.SUCCESS_CREATED);

		Map<String, Object> info = new HashMap<String, Object>();
		info.put("key", Disk.UUID_KEY);
		info.put("value", disk.getUuid());

		return createTemplateRepresentation("json/keyvalue.ftl", info,
				APPLICATION_JSON);

	}

	@Post("multipart")
	public void upload(Representation entity) {

		if (entity == null) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					"post with null entity");
		}

		Disk disk = saveAndInflateFiles();

		validateNewDisk(disk);

		DiskUtils.createReadOnlyDisk(disk);
		disk.setSeed(true);

		createDisk(disk);

		redirectSeeOther(getBaseUrl() + "/disks/" + disk.getUuid());

	}

	protected void createDisk(Disk disk) {
		DiskUtils.createDisk(disk);
	}

	private Disk saveAndInflateFiles() {

		int fileSizeLimit = ServiceConfiguration.getInstance().UPLOAD_COMPRESSED_IMAGE_MAX_BYTES;

		DiskFileItemFactory factory = new DiskFileItemFactory();
		factory.setSizeThreshold(fileSizeLimit);

		RestletFileUpload upload = new RestletFileUpload(factory);

		List<FileItem> items;

		try {
			items = upload.parseRequest(getRequest());
		} catch (FileUploadException e) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					e.getMessage());
		}

		Disk disk = null;
		for (FileItem fi : items) {
			if (fi.getName() != null) {
				disk = inflateAndProcessImage(fi);
			}
		}
		if (disk == null) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					"empty file uploaded");
		}

		// Return only the last uuid
		return disk;
	}

	protected Disk inflateAndProcessImage(FileItem fi) {
		Disk disk = initializeDisk();
		String compressedFilename = FileUtils.getCompressedDiskLocation(disk
				.getUuid());

		File file = new File(compressedFilename);

		try {
			fi.write(file);
		} catch (Exception ex) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					"no valid file uploaded");
		}
		String cachedDiskLocation = FileUtils.getCachedDiskLocation(disk
				.getUuid());
		long size = inflateFile(file, cachedDiskLocation);
		disk.setSize(size);
		try {
			disk.setIdentifier(DiskUtils.calculateHash(new File(
					cachedDiskLocation)));
		} catch (FileNotFoundException e) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					e.getMessage());
		}
		validateNewDisk(disk);
		return disk;
	}

	private long inflateFile(File file, String inflatedName) {
		GZIPInputStream in = null;
		OutputStream out = null;
		try {
			in = new GZIPInputStream(new FileInputStream(file));

			out = new FileOutputStream(inflatedName);

			byte[] buf = new byte[1024];
			int len;
			while ((len = in.read(buf)) > 0) {
				out.write(buf, 0, len);
			}

		} catch (IOException e) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					e.getMessage());
		} finally {
			try {
				if (in != null) {
					in.close();
				}
			} catch (IOException e) {
				// it's ok
			}
			try {
				if (out != null) {
					out.close();
				}
			} catch (IOException e) {
				// it's ok
			}
		}
		return new File(inflatedName).length();
	}

	private Map<String, Object> listDisks() {
		Map<String, Object> info = createInfoStructure("Disks");

		addCreateFormDefaults(info);

		String username = getUsername(getRequest());
		List<DiskView> disks;
		if (isSuperUser(username)) {
			disks = Disk.listAll();
		} else {
			disks = Disk.listAllByUser(username);
		}
		info.put("disks", disks);

		return info;
	}

}
