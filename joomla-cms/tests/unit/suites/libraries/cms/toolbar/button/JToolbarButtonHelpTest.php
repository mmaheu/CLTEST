<?php
/**
 * @package	    Joomla.UnitTest
 * @subpackage  Toolbar
 *
 * @copyright   Copyright (C) 2005 - 2013 Open Source Matters, Inc. All rights reserved.
 * @license	    GNU General Public License version 2 or later; see LICENSE
 */

/**
 * Test class for JToolbarButtonHelp.
 *
 * @package     Joomla.UnitTest
 * @subpackage  Toolbar
 * @since       3.0
 */
class JToolbarButtonHelpTest extends PHPUnit_Framework_TestCase
{
	/**
	 * Toolbar object
	 *
	 * @var    JToolbar
	 * @since  3.0
	 */
	protected $toolbar;

	/**
	 * Object under test
	 *
	 * @var    JToolbarButtonHelp
	 * @since  3.0
	 */
	protected $object;

	/**
	 * Sets up the fixture, for example, opens a network connection.
	 * This method is called before a test is executed.
	 *
	 * @return  void
	 *
	 * @since   3.0
	 */
	protected function setUp()
	{
		parent::setUp();

		$this->toolbar = JToolbar::getInstance();
		$this->object  = $this->toolbar->loadButtonType('help');
	}

	/**
	 * @todo   Implement testFetchButton().
	 */
	public function testFetchButton()
	{
		// Remove the following lines when you implement this test.
		$this->markTestIncomplete('This test has not been implemented yet.'
		);
	}

	/**
	 * Tests the fetchId method
	 *
	 * @return  void
	 *
	 * @since   3.0
	 */
	public function testFetchId()
	{
		$this->assertThat(
			$this->object->fetchId(),
			$this->equalTo('toolbar-help')
		);
	}
}
