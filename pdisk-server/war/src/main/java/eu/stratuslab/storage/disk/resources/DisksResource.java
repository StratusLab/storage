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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
import eu.stratuslab.storage.disk.utils.DiskProperties;
import eu.stratuslab.storage.disk.utils.DiskUtils;
import eu.stratuslab.storage.disk.utils.FileUtils;

public class DisksResource extends DiskBaseResource {

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

		getLogger().info("DisksResource createDiskRequestFromHtml");

		Properties diskProperties = getDiskProperties(new Form(entity));

		validateDiskProperties(diskProperties);

		createDisk(diskProperties);

		String uuid = diskProperties.getProperty(DiskProperties.UUID_KEY);

		redirectSeeOther(getBaseUrl() + "/disks/" + uuid + "/");

		return null;
	}

	@Post("form:json")
	public Representation createDiskRequestFromJson(Representation entity) {

		getLogger().info("DisksResource createDiskRequestFromJson");

		Properties diskProperties = getDiskProperties(new Form(entity));

		validateDiskProperties(diskProperties);

		createDisk(diskProperties);

		String uuid = diskProperties.getProperty(DiskProperties.UUID_KEY);

		setStatus(Status.SUCCESS_CREATED);

		Map<String, Object> info = new HashMap<String, Object>();
		info.put("key", DiskProperties.UUID_KEY);
		info.put("value", uuid);

		return createTemplateRepresentation("json/keyvalue.ftl", info,
				APPLICATION_JSON);

	}

	@Post("multipart")
	public void upload(Representation entity) {

		getLogger().info("DisksResource upload");

		if (entity == null) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					"post with null entity");
		}

		Properties diskProperties = saveAndInflateFiles();

		DiskUtils.createReadOnlyDisk(diskProperties);

		validateDiskProperties(diskProperties);

		createDisk(diskProperties);

		redirectSeeOther(getBaseUrl() + "/disks/"
				+ diskProperties.getProperty(DiskProperties.UUID_KEY) + "/");

	}

	private Properties saveAndInflateFiles() {

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

		Properties diskProperties = null;
		for (FileItem fi : items) {
			if (fi.getName() != null) {
				diskProperties = inflateAndProcessImage(fi);

			}
		}
		if (diskProperties == null) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					"empty file uploaded");
		}

		// Return only the last uuid
		return diskProperties;
	}

	protected Properties inflateAndProcessImage(FileItem fi) {
		Properties diskProperties;
		diskProperties = getDiskProperties(new Form());
		String uuid = diskProperties.getProperty(DiskProperties.UUID_KEY);
		String compressedFilename = FileUtils.getCompressedDiskLocation(uuid);

		File file = new File(compressedFilename);

		try {
			fi.write(file);
		} catch (Exception ex) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					"no valid file uploaded");
		}
		String cachedDiskLocation = FileUtils.getCachedDiskLocation(uuid);
		long size = inflateFile(file, cachedDiskLocation);
		diskProperties.put(DiskProperties.DISK_SIZE_KEY, size);
		try {
			diskProperties.put(DiskProperties.DISK_TAG_KEY,
					DiskUtils.calculateHash(new File(cachedDiskLocation)));
		} catch (FileNotFoundException e) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					e.getMessage());
		}
		validateDiskProperties(diskProperties);
		return diskProperties;
	}

	private long inflateFile(File file, String inflatedName) {
		ZipFile z = constructZipFile(file);

		ZipEntry entry = z.entries().nextElement();
		try {

			copyInputStream(z.getInputStream(entry), new BufferedOutputStream(
					new FileOutputStream(inflatedName)));

		} catch (IOException e) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					e.getMessage());
		}
		return new File(inflatedName).length();
	}

	private OutputStream copyInputStream(InputStream in, OutputStream out)
			throws IOException {
		byte[] buffer = new byte[1024];
		int len;
		while ((len = in.read(buffer)) >= 0)
			out.write(buffer, 0, len);
		in.close();
		out.close();
		return out;
	}

	protected ZipFile constructZipFile(File file) {
		ZipFile z;
		try {
			z = new ZipFile(file);
		} catch (IOException e) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					e.getMessage());
		}
		if (z.size() < 1) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					"empty zip file");
		} else if (z.size() > 1) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					"zip file should only contain one raw image");
		}
		return z;
	}

	private Map<String, Object> listDisks() {
		Map<String, Object> info = createInfoStructure("Disks list");

		addCreateFormDefaults(info);

		List<Properties> diskInfoList = new LinkedList<Properties>();
		info.put("disks", diskInfoList);

		List<String> disks = zk.getDisks();
		for (String uuid : disks) {
			Properties properties = zk.getDiskProperties(uuid);

			// List only disk of the user
			if (hasSufficientRightsToView(properties)) {
				diskInfoList.add(properties);
			}
		}

		return info;
	}

	private void addCreateFormDefaults(Map<String, Object> info) {
		Map<String, Object> defaults = new HashMap<String, Object>();
		defaults.put(DiskProperties.DISK_SIZE_KEY, 1);
		defaults.put(DiskProperties.DISK_TAG_KEY, "");
		defaults.put(DiskProperties.DISK_VISIBILITY_KEY,
				DiskVisibility.PRIVATE.toString());

		info.put("values", defaults);

		List<String> visibilities = new LinkedList<String>();
		for (DiskVisibility visibility : DiskVisibility.values()) {
			visibilities.add(visibility.toString());
		}

		info.put("visibilities", visibilities);
	}

}
