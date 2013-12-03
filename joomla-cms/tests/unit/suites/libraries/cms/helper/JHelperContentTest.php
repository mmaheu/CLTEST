<?php
/**
 * @package	    Joomla.UnitTest
 * @subpackage  Helper
 *
 * @copyright   Copyright (C) 2005 - 2013 Open Source Matters, Inc. All rights reserved.
 * @license	    GNU General Public License version 2 or later; see LICENSE
 */

/**
 * Test class for JHelperCOntent.
 *
 * @package     Joomla.UnitTest
 * @subpackage  Helper
 * @since       3.2
 */
class JHelperContentTest extends TestCaseDatabase
{
	/**
	 * @var    JHelper
	 * @since  3.2
	 */
	protected $object;

	/**
	 * Gets the data set to be loaded into the database during setup
	 *
	 * @return  PHPUnit_Extensions_Database_DataSet_CsvDataSet
	 *
	 * @since   3.2
	 */
	protected function getDataSet()
	{
		$dataSet = new PHPUnit_Extensions_Database_DataSet_CsvDataSet(',', "'", '\\');

		$dataSet->addTable('jos_languages', JPATH_TEST_DATABASE . '/jos_languages.csv');
		$dataSet->addTable('jos_users', JPATH_TEST_DATABASE . '/jos_users.csv');

		return $dataSet;
	}

	/**
	 * Sets up the fixture, for example, opens a network connection.
	 * This method is called before a test is executed.
	 *
	 * @return  void
	 *
	 * @since   3.2
	 */
	protected function setUp()
	{
		parent::setUp();

		$this->object = new JHelperContent;
		JFactory::$application = $this->getMockApplication();
	}

	/**
	 * Tests the getCurrentLanguage()
	 *
	 * @return  void
	 *
	 * @since   3.2
	 */
	public function testGetCurrentLanguage()
	{
		$this->markTestSkipped('Test not implemented.');
	}

	/**
	 * getLanguageId data
	 *
	 * @return  array
	 *
	 * @since   3.2
	 */
	public function languageIdProvider()
	{
		return array(
			array('Exists' => 'en-GB', 1),
			array('Does not exit' => 'ab-CD', null),
		);
	}

	/**
	 * Tests the getLanguageId()
	 *
	 * @return  void
	 *
	 * @since   3.2
	 * @dataProvider  languageIdProvider
	 */
	public function testGetLanguageId($languageName, $expected)
	{
		$languageId = $this->object->getLanguageId($languageName);
		$this->assertEquals($languageId, $expected);
	}

	/**
	 * Tests the getActions() method
	 *
	 * @return  void
	 *
	 * @since   3.2
	 */
	public function testGetActions()
	{
		$this->markTestSkipped('Test not implemented.');
	}

	/**
	 * Tests the addSubmenu() method
	 *
	 * @return  void
	 *
	 * @since   3.2
	 */
	public function testAddSubmenu()
	{
		$this->markTestSkipped('Test should be implemented in classes extendig this.');
	}

	/*
	 *  Tests the getRowData() method
	 *
	 * @return  void
	 *
	 * @since   3.2
	 */
	public function testGetRowData()
	{
		$db = JFactory::getDbo();
		$db->setQuery('SELECT * FROM ' . $db->quoteName('#__users') . ' WHERE ' . $db->quoteName('id') . ' = ' . (int) 42);
		$arrayFromQuery =  $db->loadAssoc();

		$testTable = new JTableUser(self::$driver);
		$testTable->load(42);
		$arrayFromMethod = $this->object->getRowData($testTable);

		$this->assertEquals($arrayFromQuery, $arrayFromMethod);
	}
}
