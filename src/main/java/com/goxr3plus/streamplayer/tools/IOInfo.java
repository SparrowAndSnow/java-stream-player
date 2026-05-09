package com.goxr3plus.streamplayer.tools;


import java.nio.file.Path;

public class IOInfo {

	/**
	 * Returns the extension of file(without (.)) for example <b>(ai.mp3)->(mp3)</b>
	 * and to lowercase (Mp3 -> mp3)
	 *
	 * @param absolutePath The File absolute path
	 *
	 * @return the File extension
	 */
	public static String getFileExtension(final String absolutePath) {
		return getExtensionWithoutDot(Path.of(absolutePath)).toLowerCase();

		// int i = path.lastIndexOf('.'); // characters contained before (.)
		//
		// if the name is not empty
		// if (i > 0 && i < path.length() - 1)
		// return path.substring(i + 1).toLowerCase()
		//
		// return null
	}

	/**
	 * Returns the name of the file for example if file path is <b>(C:/Give me
	 * more/no no/media.ogg)</b> it returns <b>(media.ogg)</b>
	 *
	 * @param absolutePath the path
	 *
	 * @return the File title+extension
	 */
	public static String getFileName(final String absolutePath) {
		return getBasename(Path.of(absolutePath));
	}

	/**
	 * Returns the title of the file for example if file name is <b>(club.mp3)</b>
	 * it returns <b>(club)</b>
	 *
	 * @param absolutePath The File absolute path
	 *
	 * @return the File title
	 */
	public static String getFileTitle(final String absolutePath) {
		return getBasename(Path.of(absolutePath));
	}


	/**
	 * 获取文件的基本名称（不包含扩展名）
	 * @param path 文件路径
	 * @return 基本名称，如果文件名为空或隐藏文件（如 .gitignore）则返回原始文件名
	 */
	public static String getBasename(Path path) {
		String fileName = path.getFileName().toString();
		int dotIndex = fileName.lastIndexOf('.');
		// 如果没有点号，或者点号在开头（隐藏文件），则整个文件名作为 basename
		if (dotIndex <= 0) {
			return fileName;
		}
		return fileName.substring(0, dotIndex);
	}

	/**
	 * 获取文件的扩展名（包含点号，如 ".txt"）
	 * @param path 文件路径
	 * @return 扩展名，如果没有扩展名则返回空字符串
	 */
	public static String getExtension(Path path) {
		String fileName = path.getFileName().toString();
		int dotIndex = fileName.lastIndexOf('.');
		if (dotIndex <= 0 || dotIndex == fileName.length() - 1) {
			return "";
		}
		return fileName.substring(dotIndex);
	}

	/**
	 * 获取文件的扩展名（不包含点号）
	 * @param path 文件路径
	 * @return 扩展名（不带点），如果没有扩展名则返回空字符串
	 */
	public static String getExtensionWithoutDot(Path path) {
		String ext = getExtension(path);
		return ext.isEmpty() ? "" : ext.substring(1);
	}

}
